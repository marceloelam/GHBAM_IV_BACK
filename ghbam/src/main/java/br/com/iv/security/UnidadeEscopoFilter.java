package br.com.iv.security;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.iv.util.UnidadeUtil;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Escopo por unidade: compara a unidade enviada pela tela (query/path param) com a
 * unidade da sessao (claim "unidade" do JWT) e registra UNIDADE-SHADOW quando ela vem
 * vazia ou diferente. Com ghbam.security.unidade.bloquear=true, a divergente vira 403
 * (exceto perfis que cruzam unidades por funcao); a vazia segue so em observacao.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 20)
public class UnidadeEscopoFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(UnidadeEscopoFilter.class.getName());

    // "yunidade" e' o nome legado em bam/unidade, servidor(2)/unidade|medico, escalamedica, setores,
    // visitantes e configuracaoUnidade/hospital; sem ele essas rotas listavam qualquer unidade.
    static final Set<String> PARAMETROS_UNIDADE = Set.of("unidade", "sigla", "unid", "yunidade");

    // Historico do proprio paciente (prontuario completo, alergias) e' lido em todas as
    // unidades de proposito (continuidade do cuidado); quem pode ver ja e' decidido pelo RBAC.
    // So nesses recursos "TODAS" nao bloqueia.
    static final Set<String> RECURSOS_HISTORICO_PACIENTE = Set.of(
            "HistoricoPacienteController", "ProntuarioClinicoItemController");

    // Recursos que ja usam a unidade do token quando o parametro vem vazio (vazio nao vira "todas").
    static final Set<String> RECURSOS_UNIDADE_PELO_TOKEN = Set.of("VigilanciaEpidemiologicaController");

    // Rotas que recebem o numero do BAM. CONSULTAR BAM de outra unidade e' permitido
    // (historico/continuidade do cuidado, so registra); ALTERAR exige a unidade da sessao.
    static final Set<String> PARAMETROS_BAM = Set.of("bam", "idbam");

    // Rotas legadas alteram dados via GET (ex.: alterarcoletaps/{bam}); o nome do metodo Java
    // indica escrita.
    static final List<String> PREFIXOS_ESCRITA = List.of("alterar", "atualizar", "corrigir", "restaurar",
            "cancelar", "apagar", "excluir", "remover", "deletar", "informar", "finalizar", "encerrar",
            "salvar", "gravar", "registrar", "executar", "inserir", "criar", "incluir", "inativar", "desativar",
            "suspender");

    private static final int LIMITE_CACHE_BAM = 20000;
    private final Map<String, String> unidadePorBam = new ConcurrentHashMap<>();

    private static final long INTERVALO_LOG_MS = 10 * 60 * 1000L;
    private static final int LIMITE_CHAVES_LOG = 5000;
    private final Map<String, Long> ultimoLog = new ConcurrentHashMap<>();

    // Administracao, NIR, faturamento e BI executivo consolidam ou regulam entre unidades.
    static final List<String> PERMISSOES_MULTIUNIDADE = List.of(
            "ksuperusuario", "superusuario", "kadm", "pnir", "kfaturamento", "pbiexecutivo");

    @ConfigProperty(name = "ghbam.security.unidade.shadow", defaultValue = "true")
    boolean shadow;

    @ConfigProperty(name = "ghbam.security.unidade.bloquear", defaultValue = "true")
    boolean bloquear;

    @ConfigProperty(name = "ghbam.security.unidade.permissoes-multiunidade",
            defaultValue = "ksuperusuario,superusuario,kadm,pnir,kfaturamento,pbiexecutivo")
    List<String> permissoesMultiunidade = PERMISSOES_MULTIUNIDADE;

    // BAM de outra unidade: comeca so registrando (UNIDADE-SHADOW decision=bam-divergente);
    // ligar depois de ler os logs e confirmar que nenhum fluxo legitimo cruza unidades.
    // Lida na 1a requisicao, nao injetada: o filtro nasce na inicializacao estatica, antes do
    // ghbam-secrets.properties, e o Quarkus aborta o boot se o valor muda depois (true no secrets).
    Boolean bloquearBam;

    boolean bloquearBam() {
        Boolean valor = bloquearBam;
        if (valor == null) {
            valor = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("ghbam.security.unidade.bam.bloquear", Boolean.class).orElse(false);
            bloquearBam = valor;
        }
        return valor;
    }

    // Unidade vazia/ausente: muitos repositorios tratam como "todas as unidades". Comeca so
    // registrando (decision=vazia|ausente); ligada, vira 403 para quem nao e' multiunidade.
    // Lida na 1a requisicao pelo mesmo motivo de bloquearBam().
    Boolean bloquearVazia;

    boolean bloquearVazia() {
        Boolean valor = bloquearVazia;
        if (valor == null) {
            valor = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("ghbam.security.unidade.vazia.bloquear", Boolean.class).orElse(false);
            bloquearVazia = valor;
        }
        return valor;
    }

    // ---------------------------------------------------------------- municipio (varias prefeituras)
    // Perfis multiunidade (NIR, faturamento, adm, BI) cruzam unidades so dentro do proprio municipio.
    // Ver tudo, entre municipios, e' so do administrador da plataforma (decisao Marcelo 16/11).
    static final List<String> PERMISSOES_PLATAFORMA = List.of("ksuperusuario", "superusuario");
    private static final long INTERVALO_MUNICIPIOS_MS = 10 * 60 * 1000L;
    private final Map<String, String> municipioPorUnidade = new ConcurrentHashMap<>();
    private volatile long municipiosLidosEm;
    private volatile int totalMunicipios = -1;

    Boolean bloquearMunicipio;

    boolean bloquearMunicipio() {
        Boolean valor = bloquearMunicipio;
        if (valor == null) {
            valor = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("ghbam.security.municipio.bloquear", Boolean.class).orElse(true);
            bloquearMunicipio = valor;
        }
        return valor;
    }

    /** Municipio (normalizado) da unidade, de configurarunidade.municipiopadrao; "" se nao cadastrado. */
    String municipioDaUnidade(String unidade) {
        String sigla = UnidadeUtil.normalizar(unidade);
        if (sigla.isBlank()) {
            return "";
        }
        String chave = chaveMunicipio(sigla);
        String cache = municipioPorUnidade.get(chave);
        if (cache != null) {
            return cache;
        }
        List<?> linhas = em.createNativeQuery(
                "SELECT municipiopadrao FROM configurarunidade WHERE upper(trim(sigla)) = ?1")
                .setParameter(1, sigla).setMaxResults(1).getResultList();
        String municipio = linhas.isEmpty() || linhas.get(0) == null ? ""
                : linhas.get(0).toString().trim().toUpperCase(Locale.ROOT);
        municipioPorUnidade.put(chave, municipio);
        return municipio;
    }

    /** Mais de um municipio cadastrado? Com um so, "todas as unidades" nao cruza prefeituras. */
    boolean variosMunicipios() {
        long agora = System.currentTimeMillis();
        if (totalMunicipios < 0 || agora - municipiosLidosEm > INTERVALO_MUNICIPIOS_MS) {
            Object n = em.createNativeQuery("SELECT count(DISTINCT upper(trim(municipiopadrao))) FROM configurarunidade"
                    + " WHERE coalesce(trim(municipiopadrao), '') <> ''").getSingleResult();
            totalMunicipios = n instanceof Number num ? num.intValue() : 0;
            municipiosLidosEm = agora;
            municipioPorUnidade.clear();
        }
        return totalMunicipios > 1;
    }

    /** Unidade pedida e' de outro municipio? Com varios municipios, unidade sem municipio conta como outro. */
    boolean outroMunicipio(String sessao, String pedida) {
        String ms = municipioDaUnidade(sessao);
        String mp = municipioDaUnidade(pedida);
        if (ms.isBlank() || mp.isBlank()) {
            return variosMunicipios();
        }
        return !ms.equals(mp);
    }

    /**
     * Perfil multiunidade (nao plataforma): true quando a requisicao sai do municipio da sessao —
     * unidade de outro municipio, TODAS/vazia/ausente com varios municipios, ou gravar em BAM de outro
     * municipio. Consultar historico/BAM de outro municipio segue liberado (continuidade do cuidado).
     */
    boolean avaliarMunicipio(ContainerRequestContext requestContext, AuthenticatedUser user, String sessao) {
        String recurso = nomeRecurso();
        boolean historico = RECURSOS_HISTORICO_PACIENTE.contains(recurso);
        boolean vaziaUsaSessao = historico || RECURSOS_UNIDADE_PELO_TOKEN.contains(recurso) || usaUnidadeDaSessao();
        boolean fora = false;
        for (MultivaluedMap<String, String> parametros : List.of(requestContext.getUriInfo().getQueryParameters(),
                requestContext.getUriInfo().getPathParameters())) {
            for (Map.Entry<String, List<String>> p : parametros.entrySet()) {
                if (!PARAMETROS_UNIDADE.contains(p.getKey().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                for (String pedida : p.getValue()) {
                    String decisao = avaliar(sessao, pedida);
                    boolean saiu = ("divergente".equals(decisao) && outroMunicipio(sessao, pedida))
                            || ((("todas".equals(decisao) && !historico) || ("vazia".equals(decisao) && !vaziaUsaSessao))
                                    && variosMunicipios());
                    if (saiu) {
                        logShadow(requestContext, user, "municipio-fora", "param:" + p.getKey(), sessao, pedida);
                        fora = true;
                    }
                }
            }
        }
        if (!vaziaUsaSessao && resourceInfo != null && resourceInfo.getResourceMethod() != null && variosMunicipios()) {
            MultivaluedMap<String, String> enviados = requestContext.getUriInfo().getQueryParameters();
            for (java.lang.reflect.Parameter p : resourceInfo.getResourceMethod().getParameters()) {
                jakarta.ws.rs.QueryParam qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
                if (qp != null && PARAMETROS_UNIDADE.contains(qp.value().toLowerCase(Locale.ROOT))
                        && !enviados.containsKey(qp.value())) {
                    logShadow(requestContext, user, "municipio-fora", "ausente:" + qp.value(), sessao, "");
                    fora = true;
                }
            }
        }
        if (ehEscrita(requestContext)) {
            for (Map.Entry<String, List<String>> p : requestContext.getUriInfo().getPathParameters().entrySet()) {
                if (!PARAMETROS_BAM.contains(p.getKey().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                for (String valor : p.getValue()) {
                    try {
                        String unidadeBam = unidadeDoBam(Integer.parseInt(valor.trim()));
                        if (!unidadeBam.isBlank() && outroMunicipio(sessao, unidadeBam)) {
                            logShadow(requestContext, user, "municipio-fora", "bam:" + valor, sessao, unidadeBam);
                            fora = true;
                        }
                    } catch (RuntimeException ignored) {
                        // id invalido ou banco fora: as outras regras seguem valendo
                    }
                }
            }
            try {
                Integer idbam = bamDoRegistro(requestContext.getUriInfo().getPath());
                String unidadeBam = idbam == null ? "" : unidadeDoBam(idbam);
                if (!unidadeBam.isBlank() && outroMunicipio(sessao, unidadeBam)) {
                    logShadow(requestContext, user, "municipio-fora", "registro-bam:" + idbam, sessao, unidadeBam);
                    fora = true;
                }
            } catch (RuntimeException ignored) {
                // rota sem registro clinico conhecido
            }
        }
        return fora;
    }

    // Sessao sem unidade (token e cadastro sem unidade): sem referencia, o filtro liberaria tudo.
    // Login/troca de unidade e contexto de seguranca seguem livres para o usuario escolher a unidade.
    static final List<String> ROTAS_SEM_UNIDADE = List.of("auth/", "seguranca/");

    Boolean bloquearSemUnidade;

    boolean bloquearSemUnidade() {
        Boolean valor = bloquearSemUnidade;
        if (valor == null) {
            valor = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("ghbam.security.unidade.sem-sessao.bloquear", Boolean.class).orElse(false);
            bloquearSemUnidade = valor;
        }
        return valor;
    }

    /** Registra sessao sem unidade de usuario comum; true quando a chave manda bloquear. */
    boolean semUnidadeBloqueia(ContainerRequestContext requestContext, AuthenticatedUser user) {
        if (user.hasAnyPermission(permissoesMultiunidade)) {
            return false;
        }
        String path = requestContext.getUriInfo().getPath();
        String rota = path == null ? "" : (path.startsWith("/") ? path.substring(1) : path);
        if (ROTAS_SEM_UNIDADE.stream().anyMatch(rota::startsWith)) {
            return false;
        }
        logShadow(requestContext, user, "sessao-sem-unidade", "sessao", "", "");
        return bloquearSemUnidade();
    }

    /** Vazia/ausente conta como fora da sessao? Nunca no historico do paciente nem onde vale o token. */
    boolean vaziaBloqueia() {
        String recurso = nomeRecurso();
        return bloquearVazia() && !RECURSOS_HISTORICO_PACIENTE.contains(recurso)
                && !RECURSOS_UNIDADE_PELO_TOKEN.contains(recurso) && !usaUnidadeDaSessao();
    }

    /** Metodo ou recurso marcado com @UnidadePadraoSessao (sem unidade, usa a da sessao). */
    boolean usaUnidadeDaSessao() {
        if (resourceInfo == null) {
            return false;
        }
        java.lang.reflect.Method m = resourceInfo.getResourceMethod();
        Class<?> c = resourceInfo.getResourceClass();
        return (m != null && m.isAnnotationPresent(UnidadePadraoSessao.class))
                || (c != null && c.isAnnotationPresent(UnidadePadraoSessao.class));
    }

    // Cadastro do paciente e' um so para todas as unidades (edicao liberada, auditada com a unidade).
    static final Set<String> RECURSOS_CORPO_LIVRE = Set.of("PacientesController");

    static final String PROP_CORPO_SESSAO = "ghbam.unidade.corpo.sessao";
    static final String PROP_CORPO_RECURSO = "ghbam.unidade.corpo.recurso";

    /**
     * Escrita de usuario comum: deixa a unidade da sessao na requisicao para o
     * {@link UnidadeCorpoInterceptor} conferir a unidade que vier no corpo JSON.
     */
    void marcarCorpoParaAvaliar(ContainerRequestContext requestContext, AuthenticatedUser user, String sessao) {
        String recurso = nomeRecurso();
        if (sessao == null || sessao.isBlank() || user.hasAnyPermission(permissoesMultiunidade)
                || RECURSOS_CORPO_LIVRE.contains(recurso) || RECURSOS_HISTORICO_PACIENTE.contains(recurso)
                || !ehEscrita(requestContext)) {
            return;
        }
        String metodo = resourceInfo != null && resourceInfo.getResourceMethod() != null
                ? "#" + resourceInfo.getResourceMethod().getName() : "";
        requestContext.setProperty(PROP_CORPO_SESSAO, sessao);
        requestContext.setProperty(PROP_CORPO_RECURSO, recurso + metodo);
        requestContext.setProperty(PROP_CORPO_MATRICULA, user.matricula());
        // Decisão Marcelo 20/11/2026: relatório em PDF de outra unidade (BAM, histórico, prontuário...) é
        // VISUALIZAÇÃO, como o histórico completo: não bloqueia, fica no log. Quem pode ver cada relatório
        // continua no RBAC (RoutePermissionCatalog); gravar/alterar segue exigindo a unidade da sessão.
        if (ehRelatorioSomenteLeitura(requestContext)) {
            requestContext.setProperty(PROP_CORPO_SO_REGISTRAR, Boolean.TRUE);
        }
    }

    static final String PROP_CORPO_SO_REGISTRAR = "ghbam.unidade.corpo.soRegistrar";
    static final String PROP_CORPO_MATRICULA = "ghbam.unidade.corpo.matricula";

    static boolean ehRelatorioSomenteLeitura(ContainerRequestContext requestContext) {
        String path = String.valueOf(requestContext.getUriInfo().getPath()).replaceAll("^/+|/+$", "").toLowerCase(Locale.ROOT);
        return "POST".equalsIgnoreCase(requestContext.getMethod())
                && path.startsWith("report/")
                && !path.startsWith("report/cache/")
                && !path.startsWith("report/connection/");
    }

    /** Parametro de unidade declarado no metodo mas nao enviado: o backend recebe null ("todas"). */
    boolean avaliarAusentes(ContainerRequestContext requestContext, AuthenticatedUser user, String sessao) {
        if (resourceInfo == null || resourceInfo.getResourceMethod() == null) {
            return false;
        }
        MultivaluedMap<String, String> enviados = requestContext.getUriInfo().getQueryParameters();
        boolean bloqueia = false;
        for (java.lang.reflect.Parameter p : resourceInfo.getResourceMethod().getParameters()) {
            jakarta.ws.rs.QueryParam qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
            if (qp == null || !PARAMETROS_UNIDADE.contains(qp.value().toLowerCase(Locale.ROOT))
                    || enviados.containsKey(qp.value())) {
                continue;
            }
            logShadow(requestContext, user, "ausente", "query:" + qp.value(), sessao, "");
            bloqueia |= vaziaBloqueia();
        }
        return bloqueia;
    }

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    jakarta.persistence.EntityManager em;

    // Caches abaixo separados por municipio: id de BAM e sigla se repetem entre bancos.
    @Inject
    br.com.iv.plataforma.municipio.MunicipioRoteamento municipioRoteamento;

    private String chaveMunicipio(Object id) {
        return municipioRoteamento == null ? String.valueOf(id) : municipioRoteamento.chave(id);
    }

    // Descobre o BAM de um registro pelo id dele (o mesmo usado pelo log de auditoria).
    @Inject
    AuditPacienteResolver auditPacienteResolver;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!shadow) {
            return;
        }
        try {
            // So avalia requisicoes autenticadas (o RbacRequestFilter ja resolveu o usuario).
            if (!(requestContext.getProperty("ghbam.user") instanceof AuthenticatedUser user)
                    || user.matricula().isBlank()) {
                return;
            }
            String sessao = securityPermissionService.unidadeDaSessao(requestContext);
            if (sessao == null || sessao.isBlank()) {
                sessao = user.unidade();
            }
            if ((sessao == null || sessao.isBlank()) && semUnidadeBloqueia(requestContext, user)) {
                requestContext.setProperty("ghbam.unidade.decision", "bloqueada");
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity("{\"erro\":\"SESSAO_SEM_UNIDADE\",\"mensagem\":\"Selecione a unidade de trabalho antes de continuar.\"}")
                        .build());
                return;
            }
            boolean divergente = avaliarParametros(requestContext, user, sessao, "query",
                    requestContext.getUriInfo().getQueryParameters());
            divergente |= avaliarAusentes(requestContext, user, sessao);
            divergente |= avaliarParametros(requestContext, user, sessao, "path",
                    requestContext.getUriInfo().getPathParameters());
            divergente |= avaliarBam(requestContext, user, sessao) && bloquearBam();
            marcarCorpoParaAvaliar(requestContext, user, sessao);
            if (sessao != null && !sessao.isBlank() && user.hasAnyPermission(permissoesMultiunidade)
                    && !user.hasAnyPermission(PERMISSOES_PLATAFORMA)
                    && avaliarMunicipio(requestContext, user, sessao) && bloquearMunicipio()) {
                requestContext.setProperty("ghbam.unidade.decision", "bloqueada");
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity("{\"erro\":\"MUNICIPIO_FORA_DA_SESSAO\",\"mensagem\":\"A operacao deve pertencer ao municipio da sessao.\"}")
                        .build());
                return;
            }
            // Sem unidade na sessao (header legado) nao ha referencia para bloquear.
            if (divergente && bloquear && sessao != null && !sessao.isBlank() && !user.hasAnyPermission(permissoesMultiunidade)) {
                requestContext.setProperty("ghbam.unidade.decision", "bloqueada");
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity("{\"erro\":\"UNIDADE_FORA_DA_SESSAO\",\"mensagem\":\"A operacao deve pertencer a unidade da sessao.\"}")
                        .build());
            }
        } catch (RuntimeException ignored) {
            // Falha na avaliacao nunca pode derrubar a requisicao.
        }
    }

    /** null quando a unidade pedida e' a da sessao; senao "vazia", "todas" ou "divergente". */
    static String avaliar(String sessao, String pedida) {
        String pedidaNormalizada = UnidadeUtil.normalizar(pedida);
        if (pedidaNormalizada.isBlank()) {
            return "vazia";
        }
        if ("TODAS".equals(pedidaNormalizada)) {
            return "todas";
        }
        return pedidaNormalizada.equals(UnidadeUtil.normalizar(sessao)) ? null : "divergente";
    }

    /** Registra vazias/divergentes; devolve true se houve alguma divergente. */
    private boolean avaliarParametros(ContainerRequestContext requestContext, AuthenticatedUser user, String sessao,
            String origem, MultivaluedMap<String, String> parametros) {
        boolean divergente = false;
        for (Map.Entry<String, List<String>> parametro : parametros.entrySet()) {
            if (!PARAMETROS_UNIDADE.contains(parametro.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String pedida : parametro.getValue()) {
                String decisao = avaliar(sessao, pedida);
                if (decisao != null) {
                    logShadow(requestContext, user, decisao, origem + ":" + parametro.getKey(), sessao, pedida);
                    divergente |= "divergente".equals(decisao)
                            || ("todas".equals(decisao) && !RECURSOS_HISTORICO_PACIENTE.contains(nomeRecurso()))
                            || ("vazia".equals(decisao) && vaziaBloqueia());
                }
            }
        }
        return divergente;
    }

    /** true quando a requisicao altera dados: metodo HTTP de escrita ou GET legado com nome de escrita. */
    boolean ehEscrita(ContainerRequestContext requestContext) {
        String metodo = requestContext.getMethod();
        if (metodo != null && !"GET".equalsIgnoreCase(metodo) && !"HEAD".equalsIgnoreCase(metodo)
                && !"OPTIONS".equalsIgnoreCase(metodo)) {
            return true;
        }
        String nome = resourceInfo != null && resourceInfo.getResourceMethod() != null
                ? resourceInfo.getResourceMethod().getName().toLowerCase(Locale.ROOT)
                : "";
        return PREFIXOS_ESCRITA.stream().anyMatch(nome::startsWith);
    }

    /**
     * Registra BAM de outra unidade; devolve true so quando a requisicao ALTERA esse BAM
     * (consulta segue liberada e fica no log como bam-consulta-outra-unidade).
     */
    private boolean avaliarBam(ContainerRequestContext requestContext, AuthenticatedUser user, String sessao) {
        if (sessao == null || sessao.isBlank() || RECURSOS_HISTORICO_PACIENTE.contains(nomeRecurso())) {
            return false;
        }
        boolean escrita = ehEscrita(requestContext);
        boolean divergente = false;
        for (Map.Entry<String, List<String>> parametro : requestContext.getUriInfo().getPathParameters().entrySet()) {
            if (!PARAMETROS_BAM.contains(parametro.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String valor : parametro.getValue()) {
                int idbam;
                try {
                    idbam = Integer.parseInt(valor.trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String unidadeBam;
                try {
                    unidadeBam = unidadeDoBam(idbam);
                } catch (RuntimeException e) {
                    // Falha na consulta nao pode anular o bloqueio por parametro de unidade ja decidido.
                    continue;
                }
                // BAM inexistente ou legado sem unidade: nada a comparar.
                if ("divergente".equals(avaliar(sessao, unidadeBam))) {
                    logShadow(requestContext, user, escrita ? "bam-divergente" : "bam-consulta-outra-unidade",
                            "path:" + parametro.getKey() + "=" + idbam, sessao, unidadeBam);
                    divergente |= escrita;
                }
            }
        }
        if (escrita && !divergente) {
            divergente = avaliarRegistro(requestContext, user, sessao);
        }
        return divergente;
    }

    /**
     * Gravacao pelo id do PROPRIO registro (PUT evolucaopaciente/123, POST pedidoMedico2/55/executar):
     * acha o BAM do registro (mesmo mapa de rotas da auditoria) e compara a unidade dele com a da sessao.
     */
    private boolean avaliarRegistro(ContainerRequestContext requestContext, AuthenticatedUser user, String sessao) {
        String path = requestContext.getUriInfo().getPath();
        Integer idbam;
        try {
            idbam = bamDoRegistro(path);
        } catch (RuntimeException e) {
            return false;
        }
        if (idbam == null) {
            return false;
        }
        String unidadeBam;
        try {
            unidadeBam = unidadeDoBam(idbam);
        } catch (RuntimeException e) {
            return false;
        }
        if (!"divergente".equals(avaliar(sessao, unidadeBam))) {
            return false;
        }
        logShadow(requestContext, user, "registro-bam-divergente", "registro:" + path + " idbam=" + idbam, sessao,
                unidadeBam);
        return true;
    }

    /** BAM do registro apontado pela rota, ou null quando a rota nao e de registro clinico conhecido. */
    Integer bamDoRegistro(String path) {
        AuditRegistroPaciente.Alvo alvo = AuditRegistroPaciente.localizar(path);
        if (alvo == null || auditPacienteResolver == null) {
            return null;
        }
        String[] ref = auditPacienteResolver.referenciaDoRegistro(alvo.entidade(), alvo.id());
        // Registro so com paciente (sem BAM) nao tem unidade: o paciente e um so para todas.
        if (ref == null || !AuditPacienteResolver.TIPO_BAM.equals(ref[0])) {
            return null;
        }
        return Integer.valueOf(ref[1]);
    }

    /** Unidade gravada no BAM ("" se nao existir); cacheada porque o BAM nao muda de unidade. */
    String unidadeDoBam(int idbam) {
        String chave = chaveMunicipio(idbam);
        String cache = unidadePorBam.get(chave);
        if (cache != null) {
            return cache;
        }
        List<?> linhas = em.createNativeQuery("SELECT unidade FROM bam WHERE idbam = ?1")
                .setParameter(1, idbam).setMaxResults(1).getResultList();
        String unidade = linhas.isEmpty() || linhas.get(0) == null ? "" : linhas.get(0).toString();
        if (unidadePorBam.size() > LIMITE_CACHE_BAM) {
            unidadePorBam.clear();
        }
        unidadePorBam.put(chave, unidade);
        return unidade;
    }

    private String nomeRecurso() {
        return resourceInfo != null && resourceInfo.getResourceClass() != null
                ? resourceInfo.getResourceClass().getSimpleName()
                : "";
    }

    private void logShadow(ContainerRequestContext requestContext, AuthenticatedUser user, String decisao,
            String parametro, String sessao, String pedida) {
        String recurso = resourceInfo != null && resourceInfo.getResourceMethod() != null
                ? resourceInfo.getResourceClass().getSimpleName() + "#" + resourceInfo.getResourceMethod().getName()
                : requestContext.getUriInfo().getPath();
        // Uma linha por recurso/usuario/decisao a cada 10 min, para nao inundar o log.
        String chave = recurso + "|" + user.matricula() + "|" + decisao + "|" + UnidadeUtil.normalizar(pedida);
        long agora = System.currentTimeMillis();
        Long anterior = ultimoLog.get(chave);
        if (anterior != null && agora - anterior < INTERVALO_LOG_MS) {
            return;
        }
        if (ultimoLog.size() > LIMITE_CHAVES_LOG) {
            ultimoLog.clear();
        }
        ultimoLog.put(chave, agora);
        LOGGER.warning("UNIDADE-SHADOW decision=" + decisao
                + " method=" + requestContext.getMethod()
                + " recurso=" + recurso
                + " param=" + parametro
                + " sessao=" + UnidadeUtil.normalizar(sessao)
                + " pedida=" + UnidadeUtil.normalizar(pedida)
                + " matricula=" + user.matricula()
                + " cargo=" + user.cargo()
                + " multiunidade=" + user.hasAnyPermission(permissoesMultiunidade)
                + " bloquear=" + bloquear
                + " bloquearBam=" + bloquearBam()
                + " bloquearVazia=" + bloquearVazia()
                + " bloquearSemUnidade=" + bloquearSemUnidade());
    }
}
