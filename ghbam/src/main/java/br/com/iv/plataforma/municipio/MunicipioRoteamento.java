package br.com.iv.plataforma.municipio;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ConnectionValidator;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.quarkus.arc.Arc;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Separacao de dados por municipio: cada prefeitura tem o proprio banco.
 *
 * <p>O municipio da requisicao vem do ENDERECO pelo qual a pagina foi aberta (X-Forwarded-Host,
 * Host e, quando a API fica em outro dominio, o Origin que o navegador envia sozinho). Endereco
 * nao cadastrado = municipio padrao (banco da aplicacao, hoje Itaguai/hospitalit).
 *
 * <p>Desligado por padrao: com {@code ghbam.municipios.separacao.ativa=false} tudo vai para o
 * banco da aplicacao, exatamente como antes. Ligar exige tambem
 * {@code quarkus.hibernate-orm.multitenant=DATABASE} (propriedade de build).
 *
 * <p>Sem requisicao (jobs agendados, inicializacao) o municipio e' sempre o padrao.
 * Ver docs/plataforma/separacao-por-municipio.md.
 */
@ApplicationScoped
public class MunicipioRoteamento {

    private static final Logger LOG = Logger.getLogger(MunicipioRoteamento.class);

    /** Cabecalho aceito so com ghbam.municipios.cabecalho.aceitar=true (ensaio via curl/nginx). */
    public static final String CABECALHO = "X-Ghbam-Municipio";
    /** Claim do JWT com o municipio em que a sessao foi aberta. */
    public static final String CLAIM = "municipio";

    private static final String ATRIBUTO = "ghbam.municipio";

    private static final class TimeUnitMs {
        static final long TRINTA_SEGUNDOS = 30_000L;
    }
    private static final Pattern URL_BANCO = Pattern.compile("^(jdbc:postgresql://[^/]+/)([^?]+)(.*)$");
    private static final Pattern NOME_BANCO = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern CODIBGE = Pattern.compile("^\\d{6,7}$");

    @Inject
    AgroalDataSource padraoDs;

    private final Map<String, AgroalDataSource> fontes = new ConcurrentHashMap<>();

    /** Configuracao lida na 1a chamada (o ghbam-secrets.properties entra depois do boot estatico). */
    private volatile Ajustes ajustes;

    record Ajustes(boolean ativa, String padrao, Map<String, String> bancos, Map<String, String> hosts,
            boolean aceitarCabecalho, boolean migrar, int maxConexoes) {
    }

    Ajustes ajustes() {
        Ajustes a = ajustes;
        if (a == null) {
            a = comBancosCriados(ler(ConfigProvider.getConfig()));
            ajustes = a;
        }
        return a;
    }

    /** Marca (COMMENT ON DATABASE) dos bancos criados pela tela Municipios: "ghbam-municipio faixa=N". */
    static final String MARCA_BANCO = "ghbam-municipio";
    private static final Pattern FAIXA_MARCA = Pattern.compile("faixa=(\\d+)");

    /** Relê a configuracao e os bancos criados (depois de "Criar banco"). */
    public void recarregar() {
        ajustes = null;
    }

    /** Nome do banco do municipio na configuracao/marca (null se nao houver). */
    public String nomeBanco(String codibge) {
        return codibge == null ? null : ajustes().bancos().get(codibge.trim());
    }

    /** Fecha o pool aberto para um banco que foi apagado/recriado (o proximo uso abre outro). */
    public void esquecerPool(String codibge) {
        AgroalDataSource ds = fontes.remove(codibge);
        if (ds != null) {
            try {
                ds.close();
            } catch (RuntimeException e) {
                LOG.warn("[MUNICIPIO] erro ao fechar pool de " + codibge + ": " + e.getMessage());
            }
        }
    }

    /**
     * Soma aos bancos do ghbam.municipios.bancos os criados pela tela Municipios
     * (pg_database municipio_<ibge> com a marca). A configuracao manual sempre vence.
     */
    private Ajustes comBancosCriados(Ajustes a) {
        Map<String, Long> criados = bancosCriados();
        if (criados.isEmpty()) {
            return a;
        }
        Map<String, String> bancos = new LinkedHashMap<>(a.bancos());
        for (String cod : criados.keySet()) {
            if (!cod.equals(a.padrao())) {
                bancos.putIfAbsent(cod, "municipio_" + cod);
            }
        }
        return new Ajustes(a.ativa(), a.padrao(), Collections.unmodifiableMap(bancos), a.hosts(),
                a.aceitarCabecalho(), a.migrar(), a.maxConexoes());
    }

    /** codibge -> faixa gravada na marca do banco (0 se nao houver). */
    Map<String, Long> bancosCriados() {
        Map<String, Long> out = new LinkedHashMap<>();
        if (padraoDs == null) {
            return out;
        }
        try (java.sql.Connection c = padraoDs.getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT datname, coalesce(shobj_description(oid, 'pg_database'), '') FROM pg_database "
                                + "WHERE datname ~ '^municipio_[0-9]{6,7}$'");
                java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String marca = rs.getString(2);
                if (!marca.startsWith(MARCA_BANCO)) {
                    continue;
                }
                Matcher m = FAIXA_MARCA.matcher(marca);
                out.put(rs.getString(1).substring("municipio_".length()), m.find() ? Long.parseLong(m.group(1)) : 0L);
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOG.warn("[MUNICIPIO] nao foi possivel listar os bancos criados: " + e.getMessage());
        }
        return out;
    }

    /** Inicio da faixa de numeracao do municipio: ghbam.municipios.faixas ou a marca do banco criado. */
    public long faixa(String codibge) {
        String valor = pares(ConfigProvider.getConfig().getOptionalValue("ghbam.municipios.faixas", String.class)
                .orElse("")).get(codibge);
        if (valor != null) {
            try {
                return Long.parseLong(valor.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return bancosCriados().getOrDefault(codibge, 0L);
    }

    /** Proxima faixa livre (10 milhoes acima da maior ja usada; Itaguai fica abaixo de 10.000.000). */
    public long proximaFaixa() {
        long maior = 0;
        for (String v : pares(ConfigProvider.getConfig().getOptionalValue("ghbam.municipios.faixas", String.class)
                .orElse("")).values()) {
            try {
                maior = Math.max(maior, Long.parseLong(v.trim()));
            } catch (NumberFormatException e) {
                // entrada invalida: ignorada
            }
        }
        for (long f : bancosCriados().values()) {
            maior = Math.max(maior, f);
        }
        return maior + 10_000_000L;
    }

    static Ajustes ler(Config c) {
        boolean pedida = c.getOptionalValue("ghbam.municipios.separacao.ativa", Boolean.class).orElse(false);
        String padrao = c.getOptionalValue("ghbam.municipios.padrao", String.class).orElse("330200").trim();
        Map<String, String> bancos = pares(c.getOptionalValue("ghbam.municipios.bancos", String.class).orElse(""));
        Map<String, String> hosts = pares(c.getOptionalValue("ghbam.municipios.hosts", String.class).orElse(""));
        String multitenant = c.getOptionalValue("quarkus.hibernate-orm.multitenant", String.class).orElse("NONE");
        boolean ativa = pedida;
        if (pedida && !"DATABASE".equalsIgnoreCase(multitenant.trim())) {
            LOG.error("[MUNICIPIO] ghbam.municipios.separacao.ativa=true, mas quarkus.hibernate-orm.multitenant="
                    + multitenant + ". Separacao NAO ligada: tudo segue no banco da aplicacao.");
            ativa = false;
        }
        bancos.entrySet().removeIf(e -> {
            boolean invalido = !CODIBGE.matcher(e.getKey()).matches() || !NOME_BANCO.matcher(e.getValue()).matches()
                    || e.getKey().equals(padrao);
            if (invalido) {
                LOG.error("[MUNICIPIO] entrada ignorada em ghbam.municipios.bancos: " + e.getKey() + "=" + e.getValue());
            }
            return invalido;
        });
        hosts.entrySet().removeIf(e -> {
            boolean invalido = !e.getValue().equals(padrao) && !bancos.containsKey(e.getValue());
            if (invalido) {
                LOG.error("[MUNICIPIO] host " + e.getKey() + " aponta para municipio sem banco: " + e.getValue());
            }
            return invalido;
        });
        if (ativa) {
            LOG.info("[MUNICIPIO] separacao ligada. Padrao=" + padrao + " bancos=" + bancos.keySet()
                    + " hosts=" + hosts);
        }
        return new Ajustes(ativa, padrao, Collections.unmodifiableMap(bancos), Collections.unmodifiableMap(hosts),
                c.getOptionalValue("ghbam.municipios.cabecalho.aceitar", Boolean.class).orElse(false),
                c.getOptionalValue("ghbam.municipios.flyway.migrar", Boolean.class).orElse(false),
                c.getOptionalValue("ghbam.municipios.max-conexoes", Integer.class).orElse(20));
    }

    /** "a=b, c=d" -> mapa com chaves em minusculo. */
    static Map<String, String> pares(String texto) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String item : texto.split(",")) {
            int i = item.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = item.substring(0, i).trim().toLowerCase(Locale.ROOT);
            String v = item.substring(i + 1).trim();
            if (!k.isEmpty() && !v.isEmpty()) {
                m.put(k, v);
            }
        }
        return m;
    }

    public boolean ativa() {
        return ajustes().ativa();
    }

    public String padrao() {
        return ajustes().padrao();
    }

    public boolean ehPadrao(String codibge) {
        return codibge == null || codibge.equals(padrao());
    }

    /** Municipio fixado por executarEm (tarefas em segundo plano, sem requisicao HTTP). */
    private static final ThreadLocal<String> FORCADO = new ThreadLocal<>();

    /**
     * Roda a tarefa no banco do municipio informado, com contexto de requisicao proprio.
     * Para threads em segundo plano: capture atual() na requisicao e passe aqui.
     * Com a separacao desligada so roda a tarefa (como antes).
     */
    public void executarEm(String codibge, Runnable tarefa) {
        if (!ativa()) {
            tarefa.run();
            return;
        }
        io.quarkus.arc.ManagedContext contexto = Arc.container().requestContext();
        boolean ativar = !contexto.isActive();
        String antes = FORCADO.get();
        FORCADO.set(codibge == null || codibge.isBlank() ? padrao() : codibge.trim());
        if (ativar) {
            contexto.activate();
        }
        try {
            tarefa.run();
        } finally {
            if (ativar) {
                contexto.terminate();
            }
            if (antes == null) {
                FORCADO.remove();
            } else {
                FORCADO.set(antes);
            }
        }
    }

    /**
     * Continuidade do cuidado entre municipios (cadastro unico de pacientes: o idpaciente e' o mesmo em
     * todos os bancos): roda a consulta aqui e no banco de cada outro municipio e junta os resultados.
     * Somente leitura. Ja dentro de outro municipio (executarEm) nao repete - evita recursao.
     * Municipio fora do ar nao derruba a consulta local.
     */
    public <T> java.util.List<T> juntarDeTodos(java.util.function.Supplier<java.util.List<T>> consulta) {
        java.util.List<T> out = new java.util.ArrayList<>(consulta.get());
        if (!ativa() || FORCADO.get() != null) {
            return out;
        }
        String daqui = atual();
        for (String cod : municipios()) {
            if (cod.equals(daqui)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            java.util.List<T>[] la = new java.util.List[1];
            try {
                executarEmNovaThread(cod, () -> la[0] = consulta.get());
            } catch (RuntimeException e) {
                LOG.warn("[MUNICIPIO] consulta no municipio " + cod + " falhou: " + e.getMessage());
                continue;
            }
            if (la[0] != null) {
                out.addAll(la[0]);
            }
        }
        return out;
    }

    /**
     * Job agendado (@Scheduled) em CADA municipio: sem requisicao o banco seria sempre o padrao.
     * Um municipio por vez, cada um em thread propria (sessao Hibernate nasce no banco certo), sem
     * limite de tempo (job longo de faturamento). A chamada passa pelo proxy do bean, entao
     * @Transactional do metodo continua valendo. Falha num municipio so registra e segue.
     * Com a separacao desligada roda direto, como antes.
     */
    public static <B> void emCadaMunicipio(Class<B> bean, String job, java.util.function.Consumer<B> chamada) {
        MunicipioRoteamento r = Arc.container().instance(MunicipioRoteamento.class).get();
        if (!r.ativa()) {
            chamada.accept(Arc.container().instance(bean).get());
            return;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String cod : r.municipios()) {
            Thread t = new Thread(() -> {
                Thread.currentThread().setContextClassLoader(cl);
                try {
                    r.executarEm(cod, () -> chamada.accept(Arc.container().instance(bean).get()));
                } catch (Throwable e) {
                    LOG.warn("[MUNICIPIO] job " + job + " falhou no municipio " + cod + ": " + e.getMessage());
                }
            }, "job-" + job + "-" + cod);
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Claim "municipio" de um JWT (sem validar assinatura: use so depois de autenticar o token). */
    public String municipioDoToken(String jwt) {
        try {
            String[] partes = jwt == null ? new String[0] : jwt.split("\\.");
            if (partes.length >= 2) {
                String json = new String(java.util.Base64.getUrlDecoder().decode(partes[1]),
                        java.nio.charset.StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"" + CLAIM + "\"\\s*:\\s*\"?(\\d{6,7})").matcher(json);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (IllegalArgumentException e) {
            // token malformado: fica no padrao
        }
        return padrao();
    }

    /** Municipio com banco proprio (fora do banco da aplicacao)? */
    public boolean temBancoProprio(String codibge) {
        return codibge != null && ajustes().bancos().containsKey(codibge.trim());
    }

    /** Municipio (codigo IBGE) da requisicao atual; o padrao fora de requisicao ou com a separacao desligada. */
    public String atual() {
        Ajustes a = ajustes();
        if (!a.ativa()) {
            return a.padrao();
        }
        String forcado = FORCADO.get();
        if (forcado != null) {
            return forcado;
        }
        if (!Arc.container().requestContext().isActive()) {
            return a.padrao();
        }
        RoutingContext rc;
        try {
            rc = Arc.container().instance(CurrentVertxRequest.class).get().getCurrent();
        } catch (RuntimeException e) {
            return a.padrao();
        }
        if (rc == null) {
            return a.padrao();
        }
        Object guardado = rc.get(ATRIBUTO);
        if (guardado instanceof String s) {
            return s;
        }
        String doToken = municipioDoTokenValidado();
        if (doToken == null) {
            doToken = municipioPelaMatriculaLegada(a, rc.request().getHeader("X-GHBAM-MATRICULA"));
        }
        String codibge = resolver(a, rc.request().getHeader(CABECALHO), rc.request().getHeader("X-Forwarded-Host"),
                rc.request().getHeader("Host"), rc.request().getHeader("Origin"), doToken);
        rc.put(ATRIBUTO, codibge);
        return codibge;
    }

    /**
     * Claim "municipio" do JWT ja validado pelo Quarkus (nao do texto cru: um token forjado nao escolhe banco).
     * null sem token ou com claim ausente.
     */
    private static String municipioDoTokenValidado() {
        org.eclipse.microprofile.jwt.JsonWebToken jwt = tokenDaRequisicao();
        if (jwt == null) {
            return null;
        }
        Object claim = jwt.getClaim(CLAIM);
        String s = claim == null ? "" : claim.toString().replace("\"", "").trim();
        return s.isEmpty() ? null : s;
    }

    private static final String ATRIBUTO_TOKEN = "ghbam.municipio.token";

    private final Map<String, Object[]> municipioPorMatricula = new ConcurrentHashMap<>();
    private static final long CACHE_MATRICULA_MS = 5 * 60 * 1000L;

    /**
     * Modo legado de identificacao por cabecalho (X-GHBAM-MATRICULA, so com
     * ghbam.security.legacy-header-auth.enabled - ligado apenas em dev): sem token nao ha claim de
     * municipio, e o servidor de outra prefeitura era procurado no banco padrao (401 em cascata).
     * Matricula que nao existe no padrao e existe em UM municipio vai para esse banco. null = padrao.
     */
    String municipioPelaMatriculaLegada(Ajustes a, String matriculaCabecalho) {
        if (matriculaCabecalho == null || matriculaCabecalho.isBlank() || a.bancos().isEmpty()
                || !ConfigProvider.getConfig().getOptionalValue("ghbam.security.legacy-header-auth.enabled",
                        Boolean.class).orElse(false)) {
            return null;
        }
        String matricula = matriculaCabecalho.split(",")[0].trim();
        Object[] cache = municipioPorMatricula.get(matricula);
        if (cache != null && System.currentTimeMillis() - (Long) cache[0] < CACHE_MATRICULA_MS) {
            return (String) cache[1];
        }
        String achado = null;
        if (!existeMatricula(a.padrao(), matricula)) {
            for (String cod : a.bancos().keySet()) {
                if (existeMatricula(cod, matricula)) {
                    if (achado != null) {
                        achado = null; // em mais de um municipio: sem como decidir pelo cabecalho
                        break;
                    }
                    achado = cod;
                }
            }
        }
        if (municipioPorMatricula.size() > 5000) {
            municipioPorMatricula.clear();
        }
        municipioPorMatricula.put(matricula, new Object[] { System.currentTimeMillis(), achado });
        return achado;
    }

    /**
     * JWT da requisicao, com assinatura e validade conferidas pelo JWTParser - o mesmo que o
     * SecurityPermissionService usa. Nao usa o bean JsonWebToken: com os cabecalhos legados (x-ghbam-*)
     * outro mecanismo assume a identidade e o bean vem vazio, o que mandava a requisicao para o banco
     * padrao (sessao "inexistente" -> 401 em Nova Iguacu). null sem token valido.
     */
    public static org.eclipse.microprofile.jwt.JsonWebToken tokenDaRequisicao() {
        try {
            if (!Arc.container().requestContext().isActive()) {
                return null;
            }
            RoutingContext rc = Arc.container().instance(CurrentVertxRequest.class).get().getCurrent();
            if (rc == null) {
                return null;
            }
            Object guardado = rc.get(ATRIBUTO_TOKEN);
            if (guardado instanceof org.eclipse.microprofile.jwt.JsonWebToken t) {
                return t;
            }
            if (guardado != null) {
                return null; // ja tentou nesta requisicao e nao havia token valido
            }
            String auth = rc.request().getHeader("Authorization");
            org.eclipse.microprofile.jwt.JsonWebToken t = null;
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                try {
                    t = Arc.container().instance(io.smallrye.jwt.auth.principal.JWTParser.class).get()
                            .parse(auth.substring(7).trim());
                } catch (Exception invalido) {
                    t = null;
                }
            }
            rc.put(ATRIBUTO_TOKEN, t == null ? Boolean.FALSE : t);
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Ordem: endereco cadastrado (ghbam.municipios.hosts) > municipio do token > padrao.
     * Mesmo endereco para todos: quem decide e' o token (o superusuario troca de municipio
     * pela "Unidade ativa", que emite um token novo com o claim do municipio).
     */
    static String resolver(Ajustes a, String cabecalho, String forwardedHost, String host, String origin,
            String doToken) {
        String doEndereco = resolver(a, cabecalho, forwardedHost, host, origin);
        if (!doEndereco.equals(a.padrao()) || doToken == null) {
            return doEndereco;
        }
        return a.bancos().containsKey(doToken) ? doToken : a.padrao();
    }

    /** Municipio so pelo endereco (null-safe); padrao quando o endereco nao esta cadastrado. */
    public String municipioDoEndereco() {
        Ajustes a = ajustes();
        if (!a.ativa() || !Arc.container().requestContext().isActive()) {
            return a.padrao();
        }
        try {
            RoutingContext rc = Arc.container().instance(CurrentVertxRequest.class).get().getCurrent();
            return rc == null ? a.padrao() : resolver(a, rc.request().getHeader(CABECALHO),
                    rc.request().getHeader("X-Forwarded-Host"), rc.request().getHeader("Host"),
                    rc.request().getHeader("Origin"));
        } catch (RuntimeException e) {
            return a.padrao();
        }
    }

    /** Endereco cadastrado para algum municipio (nao o padrao por falta de cadastro)? */
    public boolean enderecoCadastrado() {
        Ajustes a = ajustes();
        if (!a.ativa() || !Arc.container().requestContext().isActive()) {
            return false;
        }
        try {
            RoutingContext rc = Arc.container().instance(CurrentVertxRequest.class).get().getCurrent();
            if (rc == null) {
                return false;
            }
            for (String c : new String[] { primeiro(rc.request().getHeader("X-Forwarded-Host")),
                    rc.request().getHeader("Host"), hostDaUrl(rc.request().getHeader("Origin")) }) {
                String h = semPorta(c);
                if (!h.isEmpty() && a.hosts().containsKey(h)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Codigo IBGE da unidade, pelo registro de unidades do banco principal; null se nao achar.
     * Usado na troca de unidade entre municipios (so superusuario).
     */
    public String municipioDaUnidade(String sigla) {
        if (sigla == null || sigla.isBlank()) {
            return null;
        }
        try (java.sql.Connection c = padraoDs.getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT CAST(codibge AS varchar) FROM configurarunidade WHERE upper(trim(sigla)) = upper(?)")) {
            ps.setString(1, sigla.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1).trim() : null;
            }
        } catch (java.sql.SQLException e) {
            LOG.warn("[MUNICIPIO] falha ao achar o municipio da unidade " + sigla + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Quem pode ativar e entrar em outros municipios: o responsavel da plataforma
     * (ghbam.seguranca.superusuario.responsavel) ou quem ele designou (superusuario_concedente ativo).
     * Conferido sempre no banco principal, para valer igual de dentro de qualquer municipio.
     * Nao basta ser superusuario: superusuario de uma prefeitura nao entra nas outras.
     */
    public boolean podeAcessarMunicipios(String matricula) {
        String m = matricula == null ? "" : matricula.trim();
        if (m.isEmpty()) {
            return false;
        }
        String responsavel = ConfigProvider.getConfig()
                .getOptionalValue("ghbam.seguranca.superusuario.responsavel", String.class).orElse("").trim();
        if (!responsavel.isEmpty() && responsavel.equals(m)) {
            return true;
        }
        try (java.sql.Connection c = padraoDs.getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM superusuario_concedente WHERE matricula = ? AND ativo")) {
            ps.setString(1, m);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (java.sql.SQLException e) {
            LOG.warn("[MUNICIPIO] falha ao conferir designado " + m + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Unidade principal (servidor.unidade) desta matricula no banco do municipio, se ela for
     * superusuario la; null sem acesso. Usada na "Unidade ativa": uma entrada por municipio.
     */
    public String unidadePrincipal(String codibge, String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return null;
        }
        try (java.sql.Connection c = dataSource(codibge).getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT trim(s.unidade) FROM servidor s JOIN servidor2 s2 ON s2.matricula = s.matricula "
                                + "WHERE s.matricula = ? AND s2.superusuario IS TRUE")) {
            ps.setString(1, matricula.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                String u = rs.next() ? rs.getString(1) : null;
                return u == null || u.isBlank() ? null : u.toUpperCase();
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOG.warn("[MUNICIPIO] sem acesso ao banco do municipio " + codibge + ": " + e.getMessage());
            return null;
        }
    }

    /** A matricula tem cadastro (servidor) no banco do municipio? Consulta leve, antes de autenticar la. */
    public boolean existeMatricula(String codibge, String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return false;
        }
        try (java.sql.Connection c = dataSource(codibge).getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement("SELECT 1 FROM servidor WHERE matricula = ?")) {
            ps.setString(1, matricula.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOG.warn("[MUNICIPIO] sem acesso ao banco do municipio " + codibge + ": " + e.getMessage());
            return false;
        }
    }

    /** Nome do municipio (configurarunidade.municipiopadrao no registro do banco principal); o codigo se faltar. */
    public String nomeMunicipio(String codibge) {
        // O nome mora no cadastro de unidades do proprio municipio; no banco padrao so existe o padrao.
        if (temBancoProprio(codibge)) {
            String proprio = nomeMunicipioEm(dataSourceSemFalha(codibge), codibge);
            if (proprio != null) {
                return proprio;
            }
        }
        String noPadrao = nomeMunicipioEm(padraoDs, codibge);
        return noPadrao != null ? noPadrao : codibge;
    }

    private AgroalDataSource dataSourceSemFalha(String codibge) {
        try {
            return dataSource(codibge);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String nomeMunicipioEm(AgroalDataSource ds, String codibge) {
        if (ds == null) {
            return null;
        }
        try (java.sql.Connection c = ds.getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT trim(municipiopadrao) FROM configurarunidade WHERE CAST(codibge AS varchar) = ? "
                                + "AND coalesce(trim(municipiopadrao), '') <> '' LIMIT 1")) {
            ps.setString(1, codibge);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (java.sql.SQLException e) {
            return null;
        }
    }

    /** Municipios (codigo IBGE) da plataforma: o padrao e os com banco proprio. */
    public java.util.List<String> municipios() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(padrao());
        out.addAll(ajustes().bancos().keySet());
        return out;
    }

    /** O cadastro desta matricula existe e e' superusuario no banco do municipio? */
    public boolean temAcesso(String codibge, String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return false;
        }
        try (java.sql.Connection c = dataSource(codibge).getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM servidor s JOIN servidor2 s2 ON s2.matricula = s.matricula "
                                + "WHERE s.matricula = ? AND s2.superusuario IS TRUE")) {
            ps.setString(1, matricula.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOG.warn("[MUNICIPIO] sem acesso ao banco do municipio " + codibge + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Unidades em que esta matricula esta habilitada no banco do municipio (servidor_unidade + unidade
     * principal do cadastro), so se for superusuario la. Vazio = sem acesso ou banco fora do ar.
     */
    public java.util.List<String> unidadesVinculadas(String codibge, String matricula) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (matricula == null || matricula.isBlank()) {
            return new java.util.ArrayList<>(out);
        }
        try (java.sql.Connection c = dataSource(codibge).getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT upper(trim(x.unidade)) FROM ("
                                + " SELECT su.unidade FROM servidor s"
                                + " JOIN servidor2 s2 ON s2.matricula = s.matricula AND s2.superusuario IS TRUE"
                                + " JOIN servidor_unidade su ON su.idservidor = s.idservidor WHERE s.matricula = ?"
                                + " UNION SELECT s.unidade FROM servidor s"
                                + " JOIN servidor2 s2 ON s2.matricula = s.matricula AND s2.superusuario IS TRUE"
                                + " WHERE s.matricula = ?) x WHERE coalesce(trim(x.unidade), '') <> ''")) {
            ps.setString(1, matricula.trim());
            ps.setString(2, matricula.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOG.warn("[MUNICIPIO] sem acesso ao banco do municipio " + codibge + ": " + e.getMessage());
        }
        return new java.util.ArrayList<>(out);
    }

    /** Siglas das unidades do registro (banco principal) de municipios com banco ou do padrao. */
    public java.util.List<String[]> unidadesDaPlataforma() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try (java.sql.Connection c = padraoDs.getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT trim(sigla), CAST(codibge AS varchar) FROM configurarunidade "
                                + "WHERE coalesce(trim(sigla), '') <> '' ORDER BY 1");
                java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cod = rs.getString(2) == null ? padrao() : rs.getString(2).trim();
                if (ehPadrao(cod) || temBancoProprio(cod)) {
                    out.add(new String[] { rs.getString(1), cod });
                }
            }
        } catch (java.sql.SQLException e) {
            LOG.warn("[MUNICIPIO] falha ao listar unidades da plataforma: " + e.getMessage());
        }
        return out;
    }

    /**
     * Roda a tarefa no banco de outro municipio numa thread propria: a sessao do Hibernate da
     * requisicao atual ja esta presa ao municipio dela e nao troca de banco no meio.
     */
    public void executarEmNovaThread(String codibge, Runnable tarefa) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Throwable[] falha = new Throwable[1];
        Thread t = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(cl);
            try {
                executarEm(codibge, tarefa);
            } catch (Throwable e) {
                falha[0] = e;
            }
        }, "municipio-" + codibge);
        t.start();
        try {
            t.join(TimeUnitMs.TRINTA_SEGUNDOS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido ao acessar o municipio " + codibge, e);
        }
        if (t.isAlive()) {
            throw new IllegalStateException("Tempo esgotado ao acessar o municipio " + codibge);
        }
        if (falha[0] instanceof RuntimeException re) {
            throw re;
        }
        if (falha[0] != null) {
            throw new IllegalStateException(falha[0]);
        }
    }

    static String resolver(Ajustes a, String cabecalho, String forwardedHost, String host, String origin) {
        if (a.aceitarCabecalho()) {
            String cab = cabecalho == null ? "" : cabecalho.trim();
            if (cab.equals(a.padrao()) || a.bancos().containsKey(cab)) {
                return cab;
            }
        }
        for (String candidato : new String[] { primeiro(forwardedHost), host, hostDaUrl(origin) }) {
            String h = semPorta(candidato);
            String codibge = h.isEmpty() ? null : a.hosts().get(h);
            if (codibge != null) {
                return codibge;
            }
        }
        return a.padrao();
    }

    private static String primeiro(String s) {
        if (s == null) {
            return null;
        }
        int i = s.indexOf(',');
        return i < 0 ? s : s.substring(0, i);
    }

    static String hostDaUrl(String url) {
        if (url == null || url.isBlank() || "null".equals(url.trim())) {
            return null;
        }
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String semPorta(String host) {
        if (host == null) {
            return "";
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        int i = h.lastIndexOf(':');
        if (i > 0 && !h.endsWith("]")) {
            h = h.substring(0, i);
        }
        return h;
    }

    /** Pool do banco do municipio (o da aplicacao para o padrao). */
    public AgroalDataSource dataSource(String codibge) {
        if (ehPadrao(codibge)) {
            return padraoDs;
        }
        Ajustes a = ajustes();
        String banco = a.bancos().get(codibge);
        if (banco == null) {
            // Nunca cai no banco padrao por engano: municipio sem banco e' erro.
            throw new IllegalStateException("Municipio sem banco configurado: " + codibge);
        }
        return fontes.computeIfAbsent(codibge, k -> criar(a, k, banco));
    }

    /** Pool do municipio da requisicao atual (para JDBC direto, fora do Hibernate). */
    public AgroalDataSource dataSourceAtual() {
        return dataSource(atual());
    }

    /** Chave de cache separada por municipio (ids de BAM/paciente se repetem entre bancos). */
    public String chave(Object id) {
        return atual() + ":" + id;
    }

    static String urlDoBanco(String urlPadrao, String banco) {
        Matcher m = URL_BANCO.matcher(urlPadrao == null ? "" : urlPadrao);
        if (!m.matches()) {
            throw new IllegalStateException("quarkus.datasource.jdbc.url fora do formato jdbc:postgresql://host/banco");
        }
        return m.group(1) + banco + m.group(3).replace("applicationName=ghbam-app", "applicationName=ghbam-" + banco);
    }

    private AgroalDataSource criar(Ajustes a, String codibge, String banco) {
        Config c = ConfigProvider.getConfig();
        String url = urlDoBanco(c.getValue("quarkus.datasource.jdbc.url", String.class), banco);
        String usuario = c.getOptionalValue("quarkus.datasource.username", String.class).orElse("postgres");
        String senha = c.getOptionalValue("quarkus.datasource.password", String.class).orElse("");
        LOG.info("[MUNICIPIO] abrindo pool do municipio " + codibge + " (banco " + banco + ")");
        try {
            AgroalDataSourceConfigurationSupplier cfg = new AgroalDataSourceConfigurationSupplier()
                    .connectionPoolConfiguration(p -> p
                            .initialSize(1)
                            .minSize(1)
                            .maxSize(Math.max(2, a.maxConexoes()))
                            .acquisitionTimeout(Duration.ofSeconds(60))
                            .maxLifetime(Duration.ofMinutes(10))
                            .reapTimeout(Duration.ofMinutes(5))
                            .validationTimeout(Duration.ofMinutes(2))
                            .validateOnBorrow(true)
                            .leakTimeout(Duration.ofMinutes(1))
                            .connectionValidator(ConnectionValidator.defaultValidator())
                            .connectionFactoryConfiguration(f -> f
                                    .connectionProviderClassName("org.postgresql.Driver")
                                    .jdbcUrl(url)
                                    .principal(new NamePrincipal(usuario))
                                    .credential(new SimplePassword(senha))));
            AgroalDataSource ds = AgroalDataSource.from(cfg);
            if (a.migrar()) {
                migrar(c, ds, codibge);
            }
            return ds;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao abrir o banco do municipio " + codibge + ": " + e.getMessage(), e);
        }
    }

    /** Mesmas migrations do banco da aplicacao (linha V2), aplicadas ao abrir o pool. */
    private static void migrar(Config c, AgroalDataSource ds, String codibge) {
        if (!c.getOptionalValue("quarkus.flyway.migrate-at-start", Boolean.class).orElse(true)) {
            return;
        }
        Flyway.configure()
                .dataSource(ds)
                .locations(c.getOptionalValue("quarkus.flyway.locations", String.class).orElse("db/migration-v2")
                        .split(","))
                .table(c.getOptionalValue("quarkus.flyway.table", String.class).orElse("ghbam_schema_history_v2"))
                .baselineOnMigrate(true)
                .baselineVersion(c.getOptionalValue("quarkus.flyway.baseline-version", String.class).orElse("68"))
                .load()
                .migrate();
        LOG.info("[MUNICIPIO] migrations conferidas no municipio " + codibge);
    }

    @PreDestroy
    void fechar() {
        for (AgroalDataSource ds : fontes.values()) {
            try {
                ds.close();
            } catch (RuntimeException e) {
                LOG.warn("[MUNICIPIO] erro ao fechar pool: " + e.getMessage());
            }
        }
        fontes.clear();
    }
}
