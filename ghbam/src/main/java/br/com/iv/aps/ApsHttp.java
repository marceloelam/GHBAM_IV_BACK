package br.com.iv.aps;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Utilitarios comuns dos controllers APS (mesmo modelo do OdontologiaController):
 * usuario da requisicao, checagem fina de permissao (o RBAC global ja exige uma
 * das permissoes APS para api/aps), unidade/lotacao de trabalho e erros em JSON.
 *
 * Headers aceitos do frontend: X-APS-UNIDADE (sigla) e X-APS-LOTACAO (id da lotacao).
 */
public final class ApsHttp {

    private static final Logger LOG = Logger.getLogger(ApsHttp.class);

    public static final String APS = "paps";
    public static final String ACS = "papsacs";
    public static final String VACINA = "papsvacina";
    public static final String GESTAO = "papsgestao";

    private ApsHttp() {
    }

    public static AuthenticatedUser usuario(ContainerRequestContext ctx, SecurityPermissionService sps) {
        AuthenticatedUser user = (AuthenticatedUser) ctx.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : sps.resolve(ctx);
    }

    public static boolean pode(AuthenticatedUser u, String... permissoes) {
        if (u == null || u.matricula() == null || u.matricula().isBlank()) {
            return false;
        }
        // Sem bypass de kadm: no G-PEC so acessa quem tem a permissao marcada no card G-PEC.
        for (String p : permissoes) {
            if (u.hasPermission(p)) {
                return true;
            }
        }
        return false;
    }

    public static String unidade(ContainerRequestContext ctx, AuthenticatedUser u, String informada) {
        // G-PEC e por unidade logada: com JWT, vale a unidade da sessao e nada mais.
        Object sessao = ctx.getProperty(ApsUnidadeFilter.PROPRIEDADE);
        if (sessao instanceof String s0 && !s0.isBlank()) {
            return s0.split(":")[0].trim();
        }
        String s = null;
        String auth = ctx.getHeaderString("Authorization");
        boolean bearer = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7);
        // Com JWT sem unidade no token, nunca aceitar unidade vinda da requisicao:
        // so a do cadastro do servidor. Header/tela so valem no login legado (dev).
        if (!bearer) {
            s = ctx.getHeaderString("X-APS-UNIDADE");
            if (s == null || s.isBlank()) {
                s = informada;
            }
        }
        if (s == null || s.isBlank()) {
            s = u == null ? null : u.unidade();
        }
        // Unidade vazia faria as consultas devolverem todas as unidades.
        if (s == null || s.isBlank() || s.split(":")[0].isBlank()) {
            throw new IllegalArgumentException("Unidade de trabalho nao identificada. Saia e entre novamente selecionando a unidade no login.");
        }
        return s.split(":")[0].trim();
    }

    public static Long lotacao(ContainerRequestContext ctx) {
        String h = ctx.getHeaderString("X-APS-LOTACAO");
        if (h == null || h.isBlank() || !h.trim().matches("\\d+")) {
            return null;
        }
        return Long.valueOf(h.trim());
    }

    public static LocalDate data(String s, LocalDate padrao) {
        if (s == null || s.isBlank()) {
            return padrao;
        }
        return LocalDate.parse(ApsCidadaoService.isoDate(s));
    }

    public static Response proibido(String modulo) {
        return json(Response.Status.FORBIDDEN, "Acesso restrito: " + modulo + ". Solicite a permissao APS correspondente ao administrador.");
    }

    public static Response executar(Supplier<Object> acao) {
        try {
            Object r = acao.get();
            if (r == null) {
                return json(Response.Status.NOT_FOUND, "Registro nao encontrado.");
            }
            if (r instanceof Response resp) {
                return resp;
            }
            return Response.ok(r).type(MediaType.APPLICATION_JSON).build();
        } catch (IllegalArgumentException e) {
            return json(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            LOG.debug("APS conflito: " + e.getMessage());
            return json(Response.Status.CONFLICT, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Erro inesperado no modulo APS", e);
            return json(Response.Status.INTERNAL_SERVER_ERROR, "Erro inesperado no modulo APS: " + e.getMessage());
        }
    }

    public static Response json(Response.Status status, String mensagem) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("erro", mensagem == null ? status.getReasonPhrase() : mensagem)).build();
    }
}
