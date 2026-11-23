package br.com.iv.plataforma.municipio;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.jboss.logging.Logger;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * "Ativar municipio": prepara o banco novo (que nasce sem unidades e sem usuarios) para o
 * superusuario entrar e cadastrar o resto (servidores, setores, salas).
 *
 * <p>Copia, do banco padrao para o banco do municipio, numa transacao so:
 * <ul>
 * <li>as unidades (configurarunidade) com aquele codigo IBGE que ainda nao existem la (por sigla);</li>
 * <li>o cadastro do proprio superusuario que chamou (servidor + servidor2), com id NOVO do banco
 * destino - o idservidor de um banco nunca e' reaproveitado em outro (la pode ser outra pessoa).</li>
 * </ul>
 * Nada e' apagado nem alterado no banco padrao. Chamar de novo nao duplica (confere matricula/sigla).
 * Nenhum dado de paciente e' copiado.
 */
@Path("plataforma/municipios")
@Produces(MediaType.APPLICATION_JSON)
public class MunicipioAtivacaoResource {

    private static final Logger LOG = Logger.getLogger(MunicipioAtivacaoResource.class);

    @Inject
    MunicipioRoteamento roteamento;

    @Context
    ContainerRequestContext requestContext;

    /**
     * Municipios conhecidos (pelas unidades do banco principal) e a situacao de cada um:
     * PRINCIPAL (banco da aplicacao), SEM_BANCO (falta ghbam.municipios.bancos), PRONTO (banco
     * configurado, ainda sem o superusuario) ou ATIVADO.
     */
    @GET
    public Response listar() {
        if (!(requestContext.getProperty("ghbam.user") instanceof AuthenticatedUser user)
                || !(user.hasPermission("ksuperusuario") || user.hasPermission("superusuario"))
                || !roteamento.podeAcessarMunicipios(user.matricula())) {
            return erro(Response.Status.FORBIDDEN,
                    "Somente o responsável pela plataforma, ou quem ele designar, acessa os municípios.");
        }
        Map<String, Map<String, Object>> porCodigo = new LinkedHashMap<>();
        try (Connection c = roteamento.dataSource(roteamento.padrao()).getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT CAST(codibge AS varchar), coalesce(trim(municipiopadrao), ''), trim(sigla) "
                                + "FROM configurarunidade WHERE codibge IS NOT NULL ORDER BY 2, 3");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cod = rs.getString(1).trim();
                Map<String, Object> m = porCodigo.computeIfAbsent(cod, k -> {
                    Map<String, Object> novo = new LinkedHashMap<>();
                    novo.put("codibge", k);
                    novo.put("nome", "");
                    novo.put("unidades", new ArrayList<String>());
                    return novo;
                });
                if (String.valueOf(m.get("nome")).isBlank()) {
                    m.put("nome", rs.getString(2));
                }
                @SuppressWarnings("unchecked")
                List<String> unidades = (List<String>) m.get("unidades");
                unidades.add(rs.getString(3));
            }
        } catch (SQLException e) {
            LOG.error("[MUNICIPIO] falha ao listar municipios", e);
            return erro(Response.Status.INTERNAL_SERVER_ERROR, "Não foi possível listar os municípios.");
        }
        for (Map<String, Object> m : porCodigo.values()) {
            String cod = String.valueOf(m.get("codibge"));
            m.put("situacao", situacao(cod, user.matricula()));
        }
        return Response.ok(new ArrayList<>(porCodigo.values())).build();
    }

    private String situacao(String codibge, String matricula) {
        if (roteamento.ehPadrao(codibge)) {
            return "PRINCIPAL";
        }
        if (!roteamento.temBancoProprio(codibge)) {
            return "SEM_BANCO";
        }
        try (Connection c = roteamento.dataSource(codibge).getConnection()) {
            return existe(c, "SELECT 1 FROM servidor WHERE matricula = ?", matricula) ? "ATIVADO" : "PRONTO";
        } catch (SQLException | RuntimeException e) {
            LOG.warn("[MUNICIPIO] banco do municipio " + codibge + " inacessivel: " + e.getMessage());
            return "BANCO_INACESSIVEL";
        }
    }

    @POST
    @Path("{codibge}/ativar")
    public Response ativar(@PathParam("codibge") String codibge) {
        if (!(requestContext.getProperty("ghbam.user") instanceof AuthenticatedUser user)
                || !(user.hasPermission("ksuperusuario") || user.hasPermission("superusuario"))
                || !roteamento.podeAcessarMunicipios(user.matricula())) {
            return erro(Response.Status.FORBIDDEN,
                    "Somente o responsável pela plataforma, ou quem ele designar, ativa um município.");
        }
        if (!roteamento.ehPadrao(roteamento.atual())) {
            return erro(Response.Status.CONFLICT, "Ative o município pelo endereço principal do sistema.");
        }
        String cod = codibge == null ? "" : codibge.trim();
        if (roteamento.ehPadrao(cod)) {
            return erro(Response.Status.BAD_REQUEST, "Este é o município do banco principal; não há o que ativar.");
        }
        DataSource destino;
        try {
            destino = roteamento.dataSource(cod);
        } catch (IllegalStateException e) {
            return erro(Response.Status.BAD_REQUEST,
                    "Município sem banco configurado (ghbam.municipios.bancos): " + cod);
        }
        try {
            Map<String, Object> resultado = copiar(roteamento.dataSource(roteamento.padrao()), destino, cod,
                    user.matricula(), roteamento.faixa(cod));
            LOG.info("[MUNICIPIO] ativacao do municipio " + cod + " por " + user.matricula() + ": " + resultado);
            return Response.ok(resultado).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOG.error("[MUNICIPIO] falha ao ativar municipio " + cod, e);
            return erro(Response.Status.INTERNAL_SERVER_ERROR,
                    "Não foi possível ativar o município. Nada foi gravado. Detalhe: " + e.getMessage());
        }
    }

    /**
     * "Criar banco": cria municipio_<ibge> a partir do banco modelo (ghbam.municipios.modelo, padrao
     * ghbam_modelo_municipio: estrutura + catalogos + cadastro unico de pacientes via postgres_fdw, sem dados).
     * O banco nasce marcado ("ghbam-municipio faixa=N") e passa a ser roteado sem editar configuracao.
     * Depois disso o municipio fica "Pronto para ativar". Nada muda no banco principal.
     */
    @POST
    @Path("{codibge}/criar-banco")
    public Response criarBanco(@PathParam("codibge") String codibge) {
        if (!(requestContext.getProperty("ghbam.user") instanceof AuthenticatedUser user)
                || !(user.hasPermission("ksuperusuario") || user.hasPermission("superusuario"))
                || !roteamento.podeAcessarMunicipios(user.matricula())) {
            return erro(Response.Status.FORBIDDEN,
                    "Somente o responsável pela plataforma, ou quem ele designar, cria o banco de um município.");
        }
        if (!roteamento.ativa()) {
            return erro(Response.Status.CONFLICT, "A separação por município está desligada neste servidor "
                    + "(ghbam.municipios.separacao.ativa). Nenhum banco foi criado.");
        }
        if (!roteamento.ehPadrao(roteamento.atual())) {
            return erro(Response.Status.CONFLICT, "Crie o banco a partir do município principal.");
        }
        String cod = codibge == null ? "" : codibge.trim();
        if (!cod.matches("\\d{6,7}")) {
            return erro(Response.Status.BAD_REQUEST, "Código IBGE inválido.");
        }
        if (roteamento.ehPadrao(cod)) {
            return erro(Response.Status.BAD_REQUEST, "Este é o município do banco principal.");
        }
        // Configurado em ghbam.municipios.bancos mas apagado do servidor: recria com o mesmo nome e faixa.
        String configurado = roteamento.nomeBanco(cod);
        String banco = configurado != null ? configurado : "municipio_" + cod;
        long faixaConfigurada = roteamento.faixa(cod);
        long faixa = faixaConfigurada > 0 ? faixaConfigurada : roteamento.proximaFaixa();
        String modelo = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getOptionalValue("ghbam.municipios.modelo", String.class).orElse("ghbam_modelo_municipio").trim();
        if (!modelo.matches("[a-z][a-z0-9_]{0,62}")) {
            return erro(Response.Status.INTERNAL_SERVER_ERROR, "ghbam.municipios.modelo inválido.");
        }
        try (Connection c = roteamento.dataSource(roteamento.padrao()).getConnection()) {
            if (!existe(c, "SELECT 1 FROM configurarunidade WHERE CAST(codibge AS varchar) = ?", cod)) {
                return erro(Response.Status.BAD_REQUEST, "Nenhuma unidade com IBGE " + cod + " no banco principal.");
            }
            if (!existe(c, "SELECT 1 FROM pg_database WHERE datname = ?", modelo)) {
                return erro(Response.Status.CONFLICT, "Banco modelo \"" + modelo + "\" não existe no servidor. "
                        + "Prepare-o uma vez (docs/plataforma/banco-modelo-municipio.md) e tente de novo.");
            }
            if (existe(c, "SELECT 1 FROM pg_database WHERE datname = ?", banco)) {
                return erro(Response.Status.CONFLICT, "O banco \"" + banco + "\" já existe no servidor. "
                        + "Confira-o à mão antes de usar.");
            }
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(true); // CREATE DATABASE nao roda dentro de transacao
            try (java.sql.Statement st = c.createStatement()) {
                // Nomes validados por regex acima (identificador nao aceita parametro).
                st.execute("CREATE DATABASE \"" + banco + "\" TEMPLATE \"" + modelo + "\"");
                st.execute("COMMENT ON DATABASE \"" + banco + "\" IS '" + MunicipioRoteamento.MARCA_BANCO
                        + " faixa=" + faixa + "'");
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOG.error("[MUNICIPIO] falha ao criar o banco do municipio " + cod, e);
            String detalhe = "55006".equals(e.getSQLState())
                    ? "o banco modelo está em uso por outra conexão; feche-a e tente de novo."
                    : e.getMessage();
            return erro(Response.Status.INTERNAL_SERVER_ERROR, "Não foi possível criar o banco: " + detalhe);
        }
        roteamento.esquecerPool(cod);
        roteamento.recarregar();
        LOG.info("[MUNICIPIO] banco " + banco + " criado (modelo " + modelo + ", faixa " + faixa + ") por "
                + user.matricula());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("municipio", cod);
        r.put("banco", banco);
        r.put("faixaNumeracao", faixa + 1);
        return Response.ok(r).build();
    }

    static Map<String, Object> copiar(DataSource origem, DataSource destino, String codibge, String matricula,
            long faixa) throws SQLException {
        try (Connection src = origem.getConnection(); Connection dst = destino.getConnection()) {
            List<Map<String, Object>> unidades = linhas(src,
                    "SELECT * FROM configurarunidade WHERE CAST(codibge AS varchar) = ? ORDER BY 1", codibge);
            if (unidades.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma unidade com IBGE " + codibge
                        + " no banco principal. Cadastre a unidade em /base/unidades antes de ativar.");
            }
            List<Map<String, Object>> servidor = linhas(src, "SELECT * FROM servidor WHERE matricula = ?", matricula);
            if (servidor.isEmpty()) {
                throw new IllegalArgumentException("Cadastro do superusuário não encontrado no banco principal.");
            }
            List<Map<String, Object>> servidor2 = linhas(src, "SELECT * FROM servidor2 WHERE matricula = ?", matricula);
            String unidadePrincipal = String.valueOf(unidades.get(0).get("sigla")).trim();

            boolean autoCommit = dst.getAutoCommit();
            dst.setAutoCommit(false);
            try {
                // Faixa de numeracao do municipio ANTES de qualquer insercao: BAM, paciente/prontuario e
                // demais registros nunca repetem numero de outro municipio (decisao Marcelo 17/11).
                int contadoresAjustados = faixa > 0 ? aplicarFaixa(dst, faixa) : 0;
                List<String> unidadesCopiadas = new ArrayList<>();
                for (Map<String, Object> u : unidades) {
                    String sigla = String.valueOf(u.get("sigla")).trim();
                    if (!existe(dst, "SELECT 1 FROM configurarunidade WHERE upper(trim(sigla)) = upper(?)", sigla)) {
                        inserir(dst, "configurarunidade", u, Map.of());
                        unidadesCopiadas.add(sigla);
                    }
                }
                boolean servidorCopiado = false;
                if (!existe(dst, "SELECT 1 FROM servidor WHERE matricula = ?", matricula)) {
                    inserir(dst, "servidor", servidor.get(0), Map.of("unidade", unidadePrincipal));
                    servidorCopiado = true;
                }
                boolean permissoesCopiadas = false;
                if (!servidor2.isEmpty() && !existe(dst, "SELECT 1 FROM servidor2 WHERE matricula = ?", matricula)) {
                    inserir(dst, "servidor2", servidor2.get(0), Map.of("unidade", unidadePrincipal));
                    permissoesCopiadas = true;
                }
                // Vinculo servidor <-> unidade (V72): unidade principal do municipio, com o id NOVO do destino.
                boolean vinculoCriado = false;
                if (tabelaExiste(dst, "servidor_unidade")) {
                    try (PreparedStatement ps = dst.prepareStatement(
                            "INSERT INTO servidor_unidade (idservidor, unidade, principal, criado_em) "
                                    + "SELECT s.idservidor, ?, true, now() FROM servidor s WHERE s.matricula = ? "
                                    + "AND NOT EXISTS (SELECT 1 FROM servidor_unidade su WHERE su.idservidor = s.idservidor "
                                    + "AND upper(trim(su.unidade)) = upper(?))")) {
                        ps.setString(1, unidadePrincipal);
                        ps.setString(2, matricula);
                        ps.setString(3, unidadePrincipal);
                        vinculoCriado = ps.executeUpdate() > 0;
                    }
                }
                dst.commit();
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("faixaNumeracao", faixa > 0 ? faixa + 1 : null);
                r.put("contadoresAjustados", contadoresAjustados);
                r.put("vinculoUnidadeCriado", vinculoCriado);
                r.put("migracoesPendentes", migracoesPendentes(src, dst));
                r.put("municipio", codibge);
                r.put("unidadesCopiadas", unidadesCopiadas);
                r.put("unidadePrincipal", unidadePrincipal);
                r.put("superusuarioCopiado", servidorCopiado);
                r.put("permissoesCopiadas", permissoesCopiadas);
                return r;
            } catch (SQLException | RuntimeException e) {
                dst.rollback();
                throw e;
            } finally {
                dst.setAutoCommit(autoCommit);
            }
        }
    }

    private static List<Map<String, Object>> linhas(Connection c, String sql, String param) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> linha = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        linha.put(md.getColumnName(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                    }
                    out.add(linha);
                }
            }
        }
        return out;
    }

    /**
     * Inicio da faixa do municipio em ghbam.municipios.faixas (codibge=base, ex.: 330350=10000000).
     * 0 = sem faixa configurada (a ativacao nao mexe nos contadores e avisa no resultado).
     */
    static long faixaDoMunicipio(String codibge) {
        String texto = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getOptionalValue("ghbam.municipios.faixas", String.class).orElse("");
        String valor = MunicipioRoteamento.pares(texto).get(codibge);
        try {
            return valor == null ? 0 : Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Sobe para a faixa todo contador (sequence) do banco que esteja abaixo dela; nunca desce nenhum. */
    static int aplicarFaixa(Connection c, long base) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM (SELECT setval(format('%I.%I', schemaname, sequencename), ?, true) "
                        + "FROM pg_sequences WHERE schemaname = 'public' AND coalesce(last_value, 0) < ?) s")) {
            ps.setLong(1, base);
            ps.setLong(2, base);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static boolean tabelaExiste(Connection c, String tabela) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, "public", tabela, null)) {
            return rs.next();
        }
    }

    /**
     * Versoes aplicadas no banco principal e ausentes no do municipio (ghbam_schema_history_v2).
     * Lista nao vazia = rodar essas migrations no banco do municipio antes de usa-lo.
     */
    static List<String> migracoesPendentes(Connection src, Connection dst) throws SQLException {
        String sql = "SELECT version FROM ghbam_schema_history_v2 WHERE success AND version IS NOT NULL";
        Set<String> noDestino = new LinkedHashSet<>();
        if (tabelaExiste(dst, "ghbam_schema_history_v2")) {
            try (PreparedStatement ps = dst.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    noDestino.add(rs.getString(1));
                }
            }
        }
        List<String> pendentes = new ArrayList<>();
        try (PreparedStatement ps = src.prepareStatement(sql + " ORDER BY installed_rank"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (!noDestino.contains(rs.getString(1))) {
                    pendentes.add("V" + rs.getString(1));
                }
            }
        }
        return pendentes;
    }

    private static boolean existe(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Insere so as colunas que existem no destino, sem a chave primaria (o destino gera o id). */
    private static void inserir(Connection c, String tabela, Map<String, Object> linha, Map<String, Object> trocar)
            throws SQLException {
        Set<String> colunas = new LinkedHashSet<>();
        Set<String> chave = new LinkedHashSet<>();
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(null, "public", tabela, null)) {
            while (rs.next()) {
                colunas.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        try (ResultSet rs = md.getPrimaryKeys(null, "public", tabela)) {
            while (rs.next()) {
                chave.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (colunas.isEmpty()) {
            throw new SQLException("Tabela " + tabela + " não existe no banco do município.");
        }
        List<String> usar = new ArrayList<>();
        for (String col : linha.keySet()) {
            if (colunas.contains(col) && !chave.contains(col)) {
                usar.add(col);
            }
        }
        String sql = "INSERT INTO " + tabela + " ("
                + String.join(", ", usar.stream().map(x -> "\"" + x + "\"").toList()) + ") VALUES ("
                + String.join(", ", usar.stream().map(x -> "?").toList()) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < usar.size(); i++) {
                String col = usar.get(i);
                ps.setObject(i + 1, trocar.containsKey(col) ? trocar.get(col) : linha.get(col));
            }
            ps.executeUpdate();
        }
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("mensagem", mensagem)).build();
    }
}
