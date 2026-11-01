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

    static final Set<String> PARAMETROS_UNIDADE = Set.of("unidade", "sigla", "unid");

    // Historico do proprio paciente (prontuario completo, alergias) e' lido em todas as
    // unidades de proposito (continuidade do cuidado); quem pode ver ja e' decidido pelo RBAC.
    // So nesses recursos "TODAS" nao bloqueia.
    static final Set<String> RECURSOS_HISTORICO_PACIENTE = Set.of(
            "HistoricoPacienteController", "ProntuarioClinicoItemController");

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

    @Inject
    SecurityPermissionService securityPermissionService;

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
            boolean divergente = avaliarParametros(requestContext, user, sessao, "query",
                    requestContext.getUriInfo().getQueryParameters());
            divergente |= avaliarParametros(requestContext, user, sessao, "path",
                    requestContext.getUriInfo().getPathParameters());
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
                            || ("todas".equals(decisao) && !RECURSOS_HISTORICO_PACIENTE.contains(nomeRecurso()));
                }
            }
        }
        return divergente;
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
                + " bloquear=" + bloquear);
    }
}
