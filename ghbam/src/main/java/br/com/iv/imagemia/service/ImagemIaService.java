package br.com.iv.imagemia.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.iaauditoria.IaAuditoriaService;
import br.com.iv.imagemia.dto.CienciaImagemIaRequest;
import br.com.iv.imagemia.dto.ImagemIaAnaliseDto;
import br.com.iv.imagemia.entity.ImagemIaAnalise;
import br.com.iv.security.AuthenticatedUser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Orquestra a analise de imagem por IA (MONAI). Desligado por padrao: so roda
 * quando ghbam.monai.habilitado=true e a unidade esta na lista-piloto (por
 * sigla; vazio = todas). A IA apenas sugere; a conduta e a ciencia sao do
 * profissional. Cada analise fica registrada em imagem_ia_analise e tambem na
 * auditoria de IA (ia_chamada_auditoria), para observabilidade.
 */
@ApplicationScoped
public class ImagemIaService {

    @ConfigProperty(name = "ghbam.monai.habilitado", defaultValue = "false")
    boolean habilitado;

    @ConfigProperty(name = "ghbam.monai.unidades")
    Optional<String> unidadesPiloto;

    /** IA de tomografia (nodulo/hemorragia): desligada; hoje so raio-X em uso. */
    @ConfigProperty(name = "ghbam.monai.tc.habilitado", defaultValue = "false")
    boolean tcHabilitada;

    @Inject
    ImagemIaClient client;

    @Inject
    IaAuditoriaService auditoria;

    @Inject
    OrthancDicomService orthancService;

    @Inject
    EntityManager entityManager;

    // Executor proprio da TC: NAO herda contexto da requisicao. O padrao do
    // ManagedExecutor propaga a transacao JTA, e a transacao ativa em duas threads
    // fazia o commit da requisicao falhar (ARJUNA016053). Uma TC por vez (CPU).
    private final ManagedExecutor executor = ManagedExecutor.builder()
            .propagated(ThreadContext.NONE)
            .cleared(ThreadContext.ALL_REMAINING)
            .maxAsync(1)
            .build();

    @PreDestroy
    void encerrar() {
        executor.shutdown();
    }

    private static final Logger LOG = Logger.getLogger(ImagemIaService.class);

    /** A feature so vale se ligada, com servico configurado e unidade liberada. */
    public boolean habilitadoPara(String unidade) {
        // Config por unidade (tela /unidades) tem prioridade sobre a property global.
        ConfiguracaoUnidade cfg = configUnidade(unidade);
        if (cfg != null && Boolean.TRUE.equals(cfg.getMonaiHabilitado())
                && cfg.getMonaiUrl() != null && !cfg.getMonaiUrl().isBlank()) {
            return true;
        }
        if (!habilitado || !client.configurado()) {
            return false;
        }
        Set<String> siglas = unidadesPiloto
                .map(v -> v.split(","))
                .map(arr -> java.util.Arrays.stream(arr)
                        .map(ImagemIaService::sigla)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
        return siglas.isEmpty() || siglas.contains(sigla(unidade));
    }

    /** Mapa de calor (JPEG) de uma analise do BAM; null se nao houver. */
    public byte[] mapaCalor(int idbam, long id) {
        ImagemIaAnalise a = ImagemIaAnalise.findById(id);
        if (a == null || a.idbam == null || a.idbam != idbam || a.detalhesJson == null) {
            return null;
        }
        try {
            JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(a.detalhesJson).get("mapa_calor_jpg");
            return n == null || n.isNull() ? null : java.util.Base64.getDecoder().decode(n.asText());
        } catch (Exception e) {
            return null;
        }
    }

    public List<ImagemIaAnaliseDto> listarPorBam(int idbam) {
        List<ImagemIaAnalise> lista = ImagemIaAnalise.list("idbam = ?1 order by criadoEm desc", idbam);
        return lista.stream().map(ImagemIaAnaliseDto::de).collect(Collectors.toList());
    }

    /** Dispara a analise de um estudo DICOM e grava o achado sugerido. */
    @Transactional
    public ImagemIaAnaliseDto analisar(int idbam, Integer pacienteId, String idimagens, String descricaoExame,
                                       byte[] dicom, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        if (!habilitadoPara(usuario.unidade())) {
            throw new IllegalStateException("Analise de imagem por IA indisponivel para esta unidade.");
        }
        if (idbam <= 0) {
            throw new IllegalArgumentException("Informe o BAM.");
        }

        ImagemIaAnalise a = new ImagemIaAnalise();
        a.idbam = idbam;
        a.pacienteId = pacienteId;
        a.idimagens = idimagens;
        a.descricaoExame = descricaoExame;
        a.unidade = usuario.unidade();
        a.solicitadoPorMatricula = usuario.matricula();
        a.solicitadoPorNome = usuario.nome();
        a.criadoEm = LocalDateTime.now();
        a.status = "PENDENTE";

        long inicio = System.nanoTime();
        ImagemIaClient.ResultadoMonai r = client.analisar(dicom, descricaoExame, urlMonaiUnidade(usuario.unidade()));

        aplicarResultado(a, r);
        a.persist();

        registrarAuditoria(a, r, inicio);
        return ImagemIaAnaliseDto.de(a);
    }

    private static void aplicarResultado(ImagemIaAnalise a, ImagemIaClient.ResultadoMonai r) {
        if (r.ok()) {
            a.status = "CONCLUIDA";
            a.achado = r.achado();
            a.severidade = r.severidade();
            a.score = r.score();
            a.modelo = r.modelo();
            a.versaoModelo = r.versaoModelo();
            a.detalhesJson = r.detalhesJson();
            a.erro = null;
        } else {
            a.status = "ERRO";
            a.achado = null;
            a.erro = r.erro() != null && r.erro().length() > 240 ? r.erro().substring(0, 240) : r.erro();
        }
    }

    /**
     * Tomografia: escolhe a serie axial, confere o paciente, grava a analise como
     * PENDENTE e processa em segundo plano (volume inteiro em CPU leva minutos).
     * O selo do atendimento ja consulta periodicamente e mostra o resultado.
     */
    private ImagemIaAnaliseDto iniciarTc(int idbam, Integer pacienteExame, String idImagem, String descricao,
                                         String orthancUrl, JsonNode series, AuthenticatedUser usuario) {
        JsonNode escolhida = OrthancDicomService.escolherSerie(series);
        if (escolhida == null) {
            throw new IllegalArgumentException("Tomografia sem serie axial elegivel para a IA (precisa de "
                    + OrthancDicomService.MIN_CORTES + " a " + OrthancDicomService.MAX_CORTES
                    + " cortes; scout e reconstrucoes coronal/sagital nao servem).");
        }
        OrthancDicomService.SerieTc serie;
        try {
            serie = orthancService.descreverSerie(orthancUrl, escolhida);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao consultar o Orthanc: " + e.getMessage());
        }
        // Seguranca do paciente: a serie precisa ser do mesmo paciente do exame.
        if (!OrthancDicomService.mesmoPaciente(serie.patientId(), pacienteExame)) {
            throw new IllegalArgumentException(
                    "A imagem no Orthanc nao confere com o paciente do exame (PatientID divergente ou ausente). "
                    + "Analise bloqueada; verifique o cadastro no equipamento.");
        }
        // Clique repetido nao dispara outro processamento pesado: devolve o que ja esta rodando.
        ImagemIaAnalise emAndamento = ImagemIaAnalise.find(
                "idbam = ?1 and idimagens = ?2 and status = 'PENDENTE' and criadoEm > ?3 order by criadoEm desc",
                idbam, idImagem, LocalDateTime.now().minusMinutes(ImagemIaAnaliseDto.MINUTOS_MAX_PENDENTE))
                .firstResult();
        if (emAndamento != null) {
            return ImagemIaAnaliseDto.de(emAndamento);
        }

        ImagemIaAnalise a = new ImagemIaAnalise();
        a.idbam = idbam;
        a.pacienteId = pacienteExame;
        a.idimagens = idImagem;
        a.descricaoExame = descricao;
        a.unidade = usuario.unidade();
        a.solicitadoPorMatricula = usuario.matricula();
        a.solicitadoPorNome = usuario.nome();
        a.criadoEm = LocalDateTime.now();
        a.status = "PENDENTE";
        a.achado = "Em analise: tomografia com " + serie.cortes() + " cortes"
                + (serie.descricao().isBlank() ? "" : " (serie " + serie.descricao() + ")")
                + ". Pode levar alguns minutos.";
        // Transacao propria: o registro precisa estar gravado antes do processamento comecar.
        QuarkusTransaction.requiringNew().run(a::persist);

        Long id = a.id;
        String urlMonai = urlMonaiUnidade(usuario.unidade());
        // Excecao dentro do runAsync some sem rastro: registra sempre.
        // Sem contexto da requisicao na thread: grava no banco do municipio de quem pediu.
        br.com.iv.plataforma.municipio.MunicipioRoteamento municipios = io.quarkus.arc.Arc.container()
                .instance(br.com.iv.plataforma.municipio.MunicipioRoteamento.class).get();
        String municipio = municipios.atual();
        executor.runAsync(() -> municipios.executarEm(municipio,
                () -> processarTc(id, orthancUrl, serie, descricao, urlMonai)))
                .exceptionally(e -> {
                    LOG.errorf(e, "Analise de TC %d falhou fora do tratamento", id);
                    return null;
                });
        return ImagemIaAnaliseDto.de(a);
    }

    private void processarTc(Long id, String orthancUrl, OrthancDicomService.SerieTc serie,
                             String descricao, String urlMonai) {
        long inicio = System.nanoTime();
        LOG.infof("Analise de TC %d iniciada (%d cortes)", id, serie.cortes());
        ImagemIaClient.ResultadoMonai r;
        try {
            byte[] zip = orthancService.baixarSerieZip(orthancUrl, serie.id());
            r = client.analisarSerie(zip, descricao, urlMonai);
        } catch (Exception e) {
            r = ImagemIaClient.ResultadoMonai.erro(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        ImagemIaClient.ResultadoMonai resultado = r;
        ImagemIaAnalise[] salvo = new ImagemIaAnalise[1];
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                ImagemIaAnalise a = ImagemIaAnalise.findById(id);
                if (a == null) return;
                aplicarResultado(a, resultado);
                salvo[0] = a;
            });
        } catch (Exception e) {
            LOG.errorf("Falha ao gravar resultado da analise de TC %d: %s", id, e.getMessage());
            return;
        }
        LOG.infof("Analise de TC %d terminou: %s em %d s", id,
                resultado.ok() ? "CONCLUIDA" : "ERRO (" + resultado.erro() + ")",
                (System.nanoTime() - inicio) / 1_000_000_000L);
        if (salvo[0] != null) {
            registrarAuditoria(salvo[0], resultado, inicio);
        }
    }

    /**
     * Analisa um exame buscando o DICOM no Orthanc interno pelo idimagens
     * (= AccessionNumber). Nao recebe upload: o backend puxa a imagem.
     */
    @Transactional
    public ImagemIaAnaliseDto analisarPorExame(int idbam, Integer pacienteId, String idImagem,
                                               String descricao, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        if (!habilitadoPara(usuario.unidade())) {
            throw new IllegalStateException("Analise de imagem por IA indisponivel para esta unidade.");
        }
        if (idImagem == null || idImagem.isBlank()) {
            throw new IllegalArgumentException("Exame sem identificador de imagem (idimagens).");
        }
        ConfiguracaoUnidade cfg = configUnidade(usuario.unidade());
        String orthancUrl = cfg == null ? null : cfg.getOrthancUrl();
        if (orthancUrl == null || orthancUrl.isBlank()) {
            throw new IllegalStateException("Orthanc nao configurado para esta unidade.");
        }
        // Paciente e BAM vem do banco, nao do navegador.
        int idimagens;
        try {
            idimagens = Integer.parseInt(idImagem.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identificador de imagem invalido.");
        }
        Object[] exame = exameDoBanco(idimagens);
        if (exame == null) {
            throw new IllegalArgumentException("Exame nao encontrado no GHBAM.");
        }
        Integer bamExame = exame[0] == null ? null : ((Number) exame[0]).intValue();
        Integer pacienteExame = exame[1] == null ? null : ((Number) exame[1]).intValue();
        if (bamExame == null || bamExame != idbam) {
            throw new IllegalArgumentException("Exame nao pertence a este BAM.");
        }
        if (pacienteId != null && !pacienteId.equals(pacienteExame)) {
            throw new IllegalArgumentException("Exame nao pertence a este paciente.");
        }
        // Tomografia (serie inteira, em segundo plano) so com ghbam.monai.tc.habilitado=true.
        // Desligada, a TC segue o fluxo de 1 imagem e o servico responde "sem modelo".
        if (tcHabilitada) {
            JsonNode series;
            try {
                series = orthancService.seriesPorAccession(orthancUrl, idImagem);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao consultar o Orthanc: " + e.getMessage());
            }
            if (OrthancDicomService.temTc(series)) {
                return iniciarTc(idbam, pacienteExame, idImagem, descricao, orthancUrl, series, usuario);
            }
        }
        OrthancDicomService.Estudo estudo = orthancService.baixarPorAccession(orthancUrl, idImagem);
        if (estudo == null || estudo.dicom() == null || estudo.dicom().length == 0) {
            throw new IllegalStateException("Exame nao encontrado no Orthanc (AccessionNumber " + idImagem + ").");
        }
        // Seguranca do paciente: a imagem do Orthanc precisa ser do mesmo paciente do exame.
        if (!OrthancDicomService.mesmoPaciente(estudo.patientId(), pacienteExame)) {
            throw new IllegalArgumentException(
                    "A imagem no Orthanc nao confere com o paciente do exame (PatientID divergente ou ausente). "
                    + "Analise bloqueada; verifique o cadastro no equipamento.");
        }
        return analisar(idbam, pacienteExame, idImagem, descricao, estudo.dicom(), usuario);
    }

    /**
     * Testa a configuracao SALVA da unidade (nao aceita URL vinda da tela, para o
     * backend nao virar ponte para enderecos arbitrarios). Nao devolve URL nem
     * credencial: so se cada servico responde e quais modelos estao ligados.
     */
    public java.util.Map<String, Object> status(String siglaUnidade) {
        ConfiguracaoUnidade cfg = configUnidade(siglaUnidade);
        if (cfg == null) {
            throw new IllegalArgumentException("Unidade nao encontrada.");
        }
        java.util.Map<String, Object> monai = new java.util.LinkedHashMap<>();
        String urlMonai = cfg.getMonaiUrl();
        monai.put("configurado", urlMonai != null && !urlMonai.isBlank());
        monai.put("habilitado", Boolean.TRUE.equals(cfg.getMonaiHabilitado()));
        if (Boolean.TRUE.equals(monai.get("configurado"))) {
            try {
                JsonNode h = client.health(urlMonai);
                monai.put("online", true);
                monai.put("raioXTorax", h.path("xrv").asBoolean(false));
                monai.put("tcCranio", h.path("ich").asBoolean(false));
                monai.put("tcTorax", h.path("nodulo").asBoolean(false));
                monai.put("versao", h.path("versao").asText(""));
            } catch (Exception e) {
                monai.put("online", false);
                monai.put("erro", "Servico de IA nao respondeu (" + e.getClass().getSimpleName() + ").");
            }
        }
        java.util.Map<String, Object> orthanc = new java.util.LinkedHashMap<>();
        String urlOrthanc = cfg.getOrthancUrl();
        orthanc.put("configurado", urlOrthanc != null && !urlOrthanc.isBlank());
        if (Boolean.TRUE.equals(orthanc.get("configurado"))) {
            try {
                int http = orthancService.testar(urlOrthanc);
                orthanc.put("online", http > 0);
                orthanc.put("autenticado", http / 100 == 2);
                if (http == 401 || http == 403) {
                    orthanc.put("erro", OrthancDicomService.basicAuth(urlOrthanc) == null
                            ? "Orthanc exige usuario:senha na URL."
                            : "Orthanc recusou o usuario/senha.");
                } else if (http / 100 != 2) {
                    orthanc.put("erro", "Orthanc respondeu HTTP " + http + ".");
                }
            } catch (Exception e) {
                orthanc.put("online", false);
                orthanc.put("erro", "Orthanc nao respondeu (" + e.getClass().getSimpleName() + ").");
            }
        }
        return java.util.Map.of("monai", monai, "orthanc", orthanc);
    }

    @Transactional
    public ImagemIaAnaliseDto registrarCiencia(CienciaImagemIaRequest request, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        if (request == null || request.getId() == null || request.getIdbam() == null) {
            throw new IllegalArgumentException("Informe o BAM e a analise.");
        }
        String conduta = request.getConduta() == null ? "" : request.getConduta().trim();
        if (conduta.length() < 10) {
            throw new IllegalArgumentException("Descreva a conduta tomada (minimo de 10 caracteres).");
        }
        ImagemIaAnalise a = ImagemIaAnalise.findById(request.getId());
        if (a == null || !a.idbam.equals(request.getIdbam())) {
            throw new IllegalArgumentException("Analise nao encontrada para este BAM.");
        }
        if (a.cienciaEm != null) {
            return ImagemIaAnaliseDto.de(a); // idempotente: a primeira ciencia vale
        }
        a.cienciaEm = LocalDateTime.now();
        a.cienciaMatricula = usuario.matricula();
        a.cienciaNome = usuario.nome();
        a.cienciaConduta = conduta;
        a.persist();
        return ImagemIaAnaliseDto.de(a);
    }

    private void registrarAuditoria(ImagemIaAnalise a, ImagemIaClient.ResultadoMonai r, long inicio) {
        try {
            String descricao = a.descricaoExame == null ? "" : a.descricaoExame;
            auditoria.registrar(new IaAuditoriaService.Chamada(
                    List.of(java.util.Map.of("role", "user", "content", "analise-imagem: " + descricao)),
                    "monai", r.modelo(), r.ok() ? r.achado() : null,
                    r.ok() ? null : new RuntimeException(r.erro()), inicio, null, null,
                    ImagemIaService.class.getSimpleName()));
        } catch (Exception ignore) {
            // auditoria nunca derruba o fluxo clinico
        }
    }

    /**
     * [idbam, paciente do BAM] do exame; null se nao existir. Usa o paciente do BAM
     * (nao imagens.idpacientes) porque e o que a worklist_v envia como PatientID.
     */
    private Object[] exameDoBanco(int idimagens) {
        List<?> r = entityManager.createNativeQuery(
                "SELECT i.idbam, b.paciente_idpaciente FROM imagens i "
                + "JOIN bam b ON b.idbam = i.idbam WHERE i.idimagens = :id")
                .setParameter("id", idimagens)
                .setMaxResults(1)
                .getResultList();
        return r.isEmpty() ? null : (Object[]) r.get(0);
    }

    private ConfiguracaoUnidade configUnidade(String unidade) {
        try {
            return ConfiguracaoUnidade.find("sigla", sigla(unidade)).firstResult();
        } catch (Exception e) {
            return null;
        }
    }

    /** URL do MONAI configurada para a unidade (banco); null = usa a property global. */
    private String urlMonaiUnidade(String unidade) {
        ConfiguracaoUnidade cfg = configUnidade(unidade);
        if (cfg != null && Boolean.TRUE.equals(cfg.getMonaiHabilitado())
                && cfg.getMonaiUrl() != null && !cfg.getMonaiUrl().isBlank()) {
            return cfg.getMonaiUrl();
        }
        return null;
    }

    private static String sigla(String unidade) {
        if (unidade == null) return "";
        return unidade.split(":")[0].trim().toUpperCase(Locale.ROOT);
    }
}
