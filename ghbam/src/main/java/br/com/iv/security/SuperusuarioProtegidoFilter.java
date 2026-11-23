package br.com.iv.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import br.com.iv.plataforma.municipio.MunicipioRoteamento;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Quem nao e' Super usuario nao altera nada do cadastro de um Super usuario (decisao Marcelo 17/11/2026):
 * dados (servidor), acessos (servidor2), senha, matricula, status, unidades e remocao.
 * Roda depois do RBAC (precisa do ghbam.user) e confere o alvo no banco do municipio da sessao.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 20)
public class SuperusuarioProtegidoFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(SuperusuarioProtegidoFilter.class);
    private static final Set<String> ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> POR_ID_SERVIDOR = Set.of("matricula", "ativo", "unidades", "unidade-principal");

    @Inject
    MunicipioRoteamento roteamento;

    /** Alvo da rota: tipo (MATRICULA, ID_SERVIDOR, ID_SERVIDOR2) e valor; null = rota sem alvo individual. */
    record Alvo(String tipo, String valor) {
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!ESCRITA.contains(ctx.getMethod())) {
            return;
        }
        Alvo alvo = alvo(ctx.getUriInfo().getPath());
        if (alvo == null || !(ctx.getProperty("ghbam.user") instanceof AuthenticatedUser user)) {
            return;
        }
        if (user.hasPermission("ksuperusuario") || user.hasPermission("superusuario")) {
            return;
        }
        if (alvoEhSuperusuario(alvo)) {
            LOG.warn("[SUPERUSUARIO] " + user.matricula() + " tentou alterar um Super usuario: "
                    + ctx.getMethod() + " " + ctx.getUriInfo().getPath());
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("codigo", "SUPERUSUARIO_PROTEGIDO",
                            "mensagem", "Somente um Super usuário pode alterar o cadastro de um Super usuário."))
                    .build());
            return;
        }
        // Usuario comum nao altera o cadastro de um administrativo; administrativo e Super usuario podem
        // (decisao Marcelo 19/11/2026). hasPermission ja devolve true para o Super usuario.
        if (!user.hasPermission("kadm") && alvoEhAdministracao(alvo)) {
            LOG.warn("[ADMINISTRACAO] " + user.matricula() + " tentou alterar um administrativo: "
                    + ctx.getMethod() + " " + ctx.getUriInfo().getPath());
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("codigo", "ADMINISTRACAO_PROTEGIDA",
                            "mensagem", "Somente um administrativo ou Super usuário pode alterar o cadastro de um administrativo."))
                    .build());
        }
    }

    static Alvo alvo(String caminho) {
        String limpo = (caminho == null ? "" : caminho).replaceAll("^/+|/+$", "").replaceFirst("^api/", "");
        List<String> s = List.of(limpo.split("/+"));
        if (s.size() < 3) {
            return null;
        }
        String base = s.get(0);
        String a = s.get(1);
        String b = s.get(2);
        if ("servidor".equals(base)) {
            if (("atualizar".equals(a) || "remover".equals(a)) && b.matches("\\d+")) {
                return new Alvo("ID_SERVIDOR", b);
            }
            if ("senha".equals(b)) {
                return new Alvo("MATRICULA", a);
            }
            if (a.matches("\\d+") && POR_ID_SERVIDOR.contains(b)) {
                return new Alvo("ID_SERVIDOR", a);
            }
        } else if ("servidor2".equals(base)) {
            if (("atualizar".equals(a) || "remover".equals(a)) && b.matches("\\d+")) {
                return new Alvo("ID_SERVIDOR2", b);
            }
            if ("senha".equals(b)) {
                return new Alvo("MATRICULA", a);
            }
            if ("ativo".equals(b) && a.matches("\\d+")) {
                return new Alvo("ID_SERVIDOR2", a);
            }
        }
        return null;
    }

    private boolean alvoEhSuperusuario(Alvo alvo) {
        String sql = switch (alvo.tipo()) {
            case "MATRICULA" -> "SELECT 1 FROM servidor2 WHERE trim(matricula) = trim(?) AND superusuario";
            case "ID_SERVIDOR" -> "SELECT 1 FROM servidor s JOIN servidor2 s2 ON trim(s2.matricula) = trim(s.matricula) "
                    + "WHERE s.idservidor = CAST(? AS bigint) AND s2.superusuario";
            default -> "SELECT 1 FROM servidor2 WHERE idservidor2 = CAST(? AS bigint) AND superusuario";
        };
        return existe(sql, alvo, "SUPERUSUARIO");
    }

    /** Alvo com perfil Administracao (servidor.administracao, que vira kadm na sessao). */
    private boolean alvoEhAdministracao(Alvo alvo) {
        String sql = switch (alvo.tipo()) {
            case "MATRICULA" -> "SELECT 1 FROM servidor WHERE trim(matricula) = trim(?) AND administracao";
            case "ID_SERVIDOR" -> "SELECT 1 FROM servidor WHERE idservidor = CAST(? AS bigint) AND administracao";
            default -> "SELECT 1 FROM servidor2 s2 JOIN servidor s ON trim(s.matricula) = trim(s2.matricula) "
                    + "WHERE s2.idservidor2 = CAST(? AS bigint) AND s.administracao";
        };
        return existe(sql, alvo, "ADMINISTRACAO");
    }

    private boolean existe(String sql, Alvo alvo, String tag) {
        try (Connection c = roteamento.dataSourceAtual().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, alvo.valor());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            // Na duvida, protege: sem conseguir conferir, recusa a alteracao.
            LOG.warn("[" + tag + "] nao foi possivel conferir o alvo " + alvo + ": " + e.getMessage());
            return true;
        }
    }
}
