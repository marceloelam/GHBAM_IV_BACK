package br.com.iv.util;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.security.SecurityPermissionService;

@Path("/report")
public class ReportResource {
    
    private static final Logger LOG = Logger.getLogger(ReportResource.class);
    
    // Cache de relatórios compilados
    private static final Map<String, JasperReport> REPORT_CACHE = new ConcurrentHashMap<>();
    // Cache de caminhos de sub-relatórios
    private static final Map<String, String> SUBREPORT_CACHE = new ConcurrentHashMap<>();
    
    @ConfigProperty(name = "reports.directory", defaultValue = "reports")
    String reportsDirectory;

    /** Nome do sistema impresso nos relatorios ($P{NOME_SISTEMA}). */
    @ConfigProperty(name = "ghbam.sistema.nome", defaultValue = "GHBAM")
    String nomeSistema;

    /** Descricao do sistema disponivel aos relatorios ($P{DESCRICAO_SISTEMA}). */
    @ConfigProperty(name = "ghbam.sistema.descricao", defaultValue = "Gestão Hospitalar de Boletim de Atendimento Médico")
    String descricaoSistema;

    /** Fonte oficial da aplicação para a geração de relatórios. */
    @Inject
    br.com.iv.plataforma.municipio.MunicipioRoteamento municipioRoteamento; // banco do municipio da requisicao
    
    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    @PostConstruct
    void init() {
        LOG.info("ReportResource inicializado com cache de relatórios");
    }
    
    @POST
    @Path("/{fileName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response generateReport(@PathParam("fileName") String fileName, Map<String, String> params) {
        long startTime = System.currentTimeMillis();
        LOG.debugf("Iniciando geração do relatório: %s", fileName);
        
        try {
            String d1 = processDate(params.get("datainicial"));
            String d2 = processDate(params.get("datafinal"));
            String hoje = java.time.LocalDate.now().toString();
            if (d1 == null || d1.isEmpty()) d1 = hoje;
            if (d2 == null || d2.isEmpty()) d2 = hoje;
            String xprontuario = params.get("prontuario");
            String tipo = params.get("tipo");

            
            
            // Obtém relatório do cache ou carrega
            JasperReport jasperReport = getCompiledReport(fileName + ".jrxml");
            
            // Prepara parâmetros
            Map<String, Object> parameters = prepareParameters(params, d1, d2, xprontuario);
            
            // **MÉTODO OTIMIZADO: Usa try-with-resources com a nova Conexao**
            JasperPrint jasperPrint;
            String unidade = unidadeDaSessao();
            ConfiguracaoUnidade configuracao = unidade.isBlank() ? null : configuracaoUnidadeRepository
                .find("upper(sigla) = upper(?1)", unidade).firstResult();
            // Sem JWT (chamada legada) ou unidade apontando para 127.0.0.1/localhost (valor
            // padrão do cadastro): usa o banco da aplicação. Em servidor sem Postgres local
            // o localhost da unidade recusa a conexão.
            // Unidade sem URL propria ou sessao em municipio com banco proprio: relatorio sai do banco
            // do municipio da sessao (nunca de uma URL cadastrada que aponte para outro banco).
            boolean usarBancoDaSessao = unidade.isBlank()
                    || (configuracao != null && (apontaParaHostLocal(configuracao.getUrlConexao())
                            || configuracao.getUrlConexao() == null || configuracao.getUrlConexao().isBlank()))
                    || (municipioRoteamento.ativa() && !municipioRoteamento.ehPadrao(municipioRoteamento.atual()));
            // Reimpressao de BAM de outro municipio (cadastro unico): so o boletim do BAM, so se o BAM
            // existir naquele banco; o relatorio sai do banco de origem. Qualquer outro relatorio segue
            // preso ao banco da sessao.
            String municipioBanco = params.get("municipioBanco") == null ? "" : params.get("municipioBanco").trim();
            if (!municipioBanco.isEmpty() && municipioRoteamento.ativa()
                    && !municipioBanco.equals(municipioRoteamento.atual())) {
                if (!"bamRelatorio".equals(fileName) || !municipioRoteamento.municipios().contains(municipioBanco)) {
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Relatório de outro município não permitido.").build();
                }
                int idbam;
                try {
                    idbam = Integer.parseInt(String.valueOf(params.get("bam")).trim());
                } catch (NumberFormatException e) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("BAM inválido.").build();
                }
                try (Connection connection = municipioRoteamento.dataSource(municipioBanco).getConnection()) {
                    // Cabecalho (hospital, prefeitura, logotipo) da unidade de ORIGEM do BAM, nao da sessao.
                    try (java.sql.PreparedStatement ps = connection.prepareStatement(
                            "SELECT b.unidade, c.hospital, c.prefeitura, c.logoprefeitura FROM bam b "
                                    + "LEFT JOIN configurarunidade c ON upper(c.sigla) = upper(b.unidade) "
                                    + "WHERE b.idbam = ? LIMIT 1")) {
                        ps.setInt(1, idbam);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                return Response.status(Response.Status.NOT_FOUND)
                                        .entity("BAM não localizado no município informado.").build();
                            }
                            String origem = rs.getString(1);
                            parameters.put("sigla", origem);
                            parameters.put("ssigla", origem);
                            if (rs.getString(2) != null) parameters.put("hospital", rs.getString(2));
                            if (rs.getString(3) != null) parameters.put("prefeitura", rs.getString(3));
                            String logo = rs.getString(4);
                            if (logo != null && !logo.isBlank()) {
                                parameters.put("logo", logo.replaceFirst("^data:image/[^;]+;base64,", ""));
                            }
                        }
                    }
                    LOG.infof("[HISTORICO_MUNICIPIO] reimpressao do BAM %d do municipio=%s pela sessao do municipio=%s unidade=%s",
                            idbam, municipioBanco, municipioRoteamento.atual(), unidade);
                    jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
                }
            } else if (usarBancoDaSessao) {
                try (Connection connection = municipioRoteamento.dataSourceAtual().getConnection()) {
                    jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
                }
            } else {
                if (configuracao == null || configuracao.getUrlConexao() == null
                        || !configuracao.getUrlConexao().startsWith("jdbc:postgresql://")
                        || configuracao.getUserConexao() == null || configuracao.getUserConexao().isBlank()
                        || configuracao.getPwdConexao() == null || configuracao.getPwdConexao().isBlank()) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity("A unidade ativa não possui conexão de banco configurada para relatórios.")
                        .build();
                }
                try (Conexao conexaoUnidade = Conexao.getConexao(configuracao.getUrlConexao(),
                        configuracao.getUserConexao(), configuracao.getPwdConexao())) {
                    jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, conexaoUnidade.getConnection());
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024 * 1024); // 1MB inicial
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            LOG.infof("Relatório %s gerado em %d ms. Tamanho: %d bytes", 
                     fileName, elapsedTime, outputStream.size());
            
            return Response.ok(outputStream.toByteArray())
                .header("Content-Disposition", "inline; filename=\"" + fileName + ".pdf\"")
                .header("X-Generation-Time", elapsedTime + "ms")
                .build();

        } catch (JRException | IOException | SQLException e) {
            LOG.errorf(e, "Erro ao gerar relatório %s", fileName);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Erro ao gerar o relatório: " + e.getMessage())
                .build();
        } catch (Exception e) {
            LOG.errorf(e, "Erro inesperado ao gerar relatório %s", fileName);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Erro inesperado: " + e.getMessage())
                .build();
        } finally {
            // Sugere ao GC limpar memória (opcional)
            if (System.currentTimeMillis() - startTime > 5000) { // Se demorou mais de 5 segundos
                System.gc();
            }
        }
    }
    
    private String unidadeDaSessao() {
        return securityPermissionService.unidadeDaSessao(requestContext).split(":")[0].trim();
    }

    static boolean apontaParaHostLocal(String urlConexao) {
        if (urlConexao == null) return false;
        String url = urlConexao.trim().toLowerCase();
        return url.startsWith("jdbc:postgresql://127.0.0.1")
            || url.startsWith("jdbc:postgresql://localhost")
            || url.startsWith("jdbc:postgresql://[::1]");
    }

    // ========== MÉTODOS DE CACHE (mantenha os que você já tem) ==========
    
    private JasperReport getCompiledReport(String reportFile) throws JRException, IOException {
        String cacheKey = reportFile;
        
        // Verifica cache primeiro
        if (REPORT_CACHE.containsKey(cacheKey)) {
            LOG.debugf("Cache hit para relatório: %s", reportFile);
            return REPORT_CACHE.get(cacheKey);
        }
        
        LOG.debugf("Compilando relatório: %s", reportFile);
        JasperReport report = compileReport(reportFile);
        
        // Limita tamanho do cache (max 50 relatórios)
        if (REPORT_CACHE.size() >= 50) {
            String firstKey = REPORT_CACHE.keySet().iterator().next();
            REPORT_CACHE.remove(firstKey);
            LOG.debugf("Removendo do cache: %s", firstKey);
        }
        
        REPORT_CACHE.put(cacheKey, report);
        LOG.debugf("Adicionado ao cache: %s. Tamanho cache: %d", cacheKey, REPORT_CACHE.size());
        
        return report;
    }
    
    private JasperReport compileReport(String reportFile) throws JRException, IOException {
        // O JRXML e a fonte oficial; nao deixar um .jasper antigo ocultar alteracoes.
        InputStream sourceStream = findResourceAsStream(reportFile);
        if (sourceStream != null) {
            LOG.debugf("Compilando relatorio fonte: %s", reportFile);
            try (sourceStream) {
                return JasperCompileManager.compileReport(sourceStream);
            }
        }
        // 1. Primeiro tenta carregar .jasper compilado (mais rápido)
        String compiledPath = reportFile.replace(".jrxml", ".jasper");
        InputStream compiledStream = findResourceAsStream(compiledPath);
        
        if (compiledStream != null) {
            LOG.debugf("Carregando relatório compilado: %s", compiledPath);
            try (compiledStream) {
                return (JasperReport) JRLoader.loadObject(compiledStream);
            }
        }
        
        // 2. Se não encontrou .jasper, compila do .jrxml
        InputStream jrxmlStream = findResourceAsStream(reportFile);
        if (jrxmlStream == null) {
            throw new FileNotFoundException("Arquivo de relatório não encontrado: " + reportFile);
        }
        
        try (jrxmlStream) {
            return JasperCompileManager.compileReport(jrxmlStream);
        }
    }
    
    private InputStream findResourceAsStream(String fileName) throws IOException {
        // 1. Classpath (/reports/)
        String classpathPath = "/reports/" + fileName;
        InputStream is = getClass().getResourceAsStream(classpathPath);
        if (is != null) {
            LOG.debugf("Encontrado no classpath: %s", classpathPath);
            return is;
        }
        
        // 2. Diretório configurado
        java.nio.file.Path externalPath = Paths.get(reportsDirectory, fileName).normalize();
        if (Files.exists(externalPath)) {
            LOG.debugf("Encontrado no diretório: %s", externalPath);
            return Files.newInputStream(externalPath);
        }
        
        // 3. Diretório absoluto
        java.nio.file.Path absolutePath = Paths.get(System.getProperty("user.dir"), "reports", fileName).normalize();
        if (Files.exists(absolutePath)) {
            LOG.debugf("Encontrado no diretório absoluto: %s", absolutePath);
            return Files.newInputStream(absolutePath);
        }
        
        return null;
    }
    
    // ========== MÉTODOS AUXILIARES (mantenha os seus) ==========
    
    private String processDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }
        try {
            return convertToDateString(dateStr);
        } catch (DateTimeParseException e) {
            LOG.warnf("Data inválida: %s. Usando valor original.", dateStr);
            return dateStr;
        }
    }
    
    private Map<String, Object> prepareParameters(Map<String, String> params, String d1, String d2, String xprontuario) {
        Map<String, Object> parameters = new HashMap<>(40);
        parameters.put("NOME_SISTEMA", nomeSistema);
        parameters.put("DESCRICAO_SISTEMA", descricaoSistema);

        // Logo 1
        String logoBase64 = params.get("logo");
        if (logoBase64 != null && logoBase64.startsWith("data:image/")) {
            logoBase64 = logoBase64.replaceFirst("^data:image/[^;]+;base64,", "");
        }
        parameters.put("logo", logoBase64);
        
        // Logo 2
        String logo2Base64 = params.get("logo2");
        if (logo2Base64 != null && logo2Base64.startsWith("data:image/")) {
            logo2Base64 = logo2Base64.replaceFirst("^data:image/[^;]+;base64,", "");
        }
        parameters.put("logo2", logo2Base64);
        
        // Sigla
        String sigla = (params.get("sigla") == null || params.get("sigla").isEmpty()) 
            ? params.get("sigla2") 
            : params.get("sigla");
        
        // Município
        String muni = (params.get("municipio") == null || params.get("municipio").isEmpty() || "TODOS".equals(params.get("municipio")))
            ? "%" 
            : params.get("municipio");
        
        // Parâmetros básicos
        parameters.put("nbam", params.get("bam"));
        parameters.put("pbam", params.get("bam"));
        parameters.put("idp", params.get("idp"));
        parameters.put("name", params.get("idp"));
        parameters.put("numerodeinternado", params.get("bam"));
        parameters.put("ssigla", sigla);
        parameters.put("gbairro", muni);
        parameters.put("sigla", sigla);
        parameters.put("setor", params.get("setor"));
        parameters.put("hospital", params.get("hospital"));
        parameters.put("prefeitura", params.get("prefeitura"));
        parameters.put("local", params.get("local"));
        parameters.put("complemento", params.get("complemento"));
        parameters.put("pcd", params.get("pcd"));
        parameters.put("tea", params.get("tea"));
        parameters.put("datainicial", d1);
        parameters.put("datafinal", d2);
        parameters.put("dt1", params.get("dt1"));
        parameters.put("dt2", params.get("dt2"));
        parameters.put("numerodoprontuario", xprontuario);
        parameters.put("pediu", params.get("tipo"));
        
        // Sub-relatórios
        parameters.put("r1triagem", getSubreportPath("subtriagem.jasper"));
        parameters.put("r1atendimentomedico", getSubreportPath("subatendimentomedico.jasper"));
        parameters.put("r1laboratorio", getSubreportPath("sublaboratorio.jasper"));
        parameters.put("r1reporpedidomedico", getSubreportPath("subpedidomedico.jasper"));
        parameters.put("r1reporinternacao", getSubreportPath("subinternacao.jasper"));
        parameters.put("r1pedido2", getSubreportPath("subpedidos2.jasper"));
        parameters.put("r1repornir", getSubreportPath("subnir.jasper"));
        parameters.put("r1reporprescricao", getSubreportPath("subprescricao.jasper"));
        parameters.put("r1anamnese", getSubreportPath("subanamnesemedico.jasper"));
        parameters.put("r1pareceresp", getSubreportPath("subrelaparecerespecialista.jasper"));
        parameters.put("r1medicacao", getSubreportPath("submedicacao.jasper"));
        parameters.put("r1evolucao", getSubreportPath("evolucao2.jasper"));
        parameters.put("subbalancoentrada", getSubreportPath("subbalancoentrada.jasper"));
        parameters.put("subbalancosaida", getSubreportPath("subbalancosaida.jasper"));
        parameters.put("subsinaisbalanco", getSubreportPath("evolucaoSinaisBal.jasper"));
        parameters.put("totaisbalanco", getSubreportPath("totaisBalanco.jasper"));
        parameters.put("alertas", getSubreportPath("evolucaoSinaisBalAlerta.jasper"));
        parameters.put("resumobal", getSubreportPath("ResumoBalanco.jasper"));
        
        parameters.put("checar", getSubreportPath("checagemAprazament.jasper"));
        
        return parameters;
    }
    
    private String getSubreportPath(String subreportFile) {
        // Cache de caminhos
        String cacheKey = "path:" + subreportFile;
        
        if (SUBREPORT_CACHE.containsKey(cacheKey)) {
            return SUBREPORT_CACHE.get(cacheKey);
        }
        
        String path = findSubreportPath(subreportFile);
        SUBREPORT_CACHE.put(cacheKey, path);
        return path;
    }
    
    private String findSubreportPath(String subreportFile) {
        // 1. Tenta classpath primeiro
        if (getClass().getResource("/reports/" + subreportFile) != null) {
            return "reports/" + subreportFile;
        }
        
        // 2. Tenta diretório configurado
        java.nio.file.Path externalPath = Paths.get(reportsDirectory, subreportFile).normalize();
        if (Files.exists(externalPath)) {
            return externalPath.toAbsolutePath().toString();
        }
        
        // 3. Tenta diretório absoluto
        java.nio.file.Path absolutePath = Paths.get(System.getProperty("user.dir"), "reports", subreportFile).normalize();
        if (Files.exists(absolutePath)) {
            return absolutePath.toAbsolutePath().toString();
        }
        
        LOG.warnf("Sub-relatório não encontrado: %s", subreportFile);
        return "reports/" + subreportFile;
    }
    
    public static String convertToDateString(String dateParam) throws DateTimeParseException {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateParam);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return zonedDateTime.format(formatter);
    }
    
    // Endpoints para gerenciamento de cache (opcional)
    @POST
    @Path("/cache/clear")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearCache() {
        REPORT_CACHE.clear();
        SUBREPORT_CACHE.clear();
        return Response.ok(Map.of(
            "status", "success",
            "message", "Cache limpo com sucesso",
            "cacheSize", REPORT_CACHE.size()
        )).build();
    }
    
    @GET
    @Path("/cache/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCacheStats() {
        return Response.ok(Map.of(
            "mainCacheSize", REPORT_CACHE.size(),
            "subreportCacheSize", SUBREPORT_CACHE.size(),
            "memoryUsageMB", (Runtime.getRuntime().totalMemory() - 
                              Runtime.getRuntime().freeMemory()) / (1024 * 1024),
            "maxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024)
        )).build();
    }
    
    @GET
    @Path("/connection/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConnectionStats() {
        // Se você implementar métodos estáticos na classe Conexao
        try {
            Map<String, Object> stats = Conexao.getConnectionStats();
            return Response.ok(stats).build();
        } catch (Exception e) {
            return Response.ok(Map.of(
                "activeConnections", "N/A",
                "message", "Estatísticas não disponíveis"
            )).build();
        }
    }
}
