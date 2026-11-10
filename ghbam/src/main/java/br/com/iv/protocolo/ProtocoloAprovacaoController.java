package br.com.iv.protocolo;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Aprovacao de protocolos em fluxograma. Consultar a situacao: quem usa os protocolos.
 * Decidir: so kadm ou matricula em ghbam.protocolos.aprovadores — conferido aqui no servidor,
 * nunca so pela tela. Quem decidiu vem do login.
 */
@Path("protocolos-fluxograma/aprovacoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProtocoloAprovacaoController {

    @Context
    ContainerRequestContext request;

    @Inject
    SecurityPermissionService security;

    @Inject
    ProtocoloAprovacaoService service;

    private AuthenticatedUser usuario() {
        AuthenticatedUser user = security.resolve(request);
        if (user == null || user.matricula() == null || user.matricula().isBlank()) throw new NotAuthorizedException("Bearer");
        if (!user.hasAnyPermission(ProtocoloExecucaoController.ACESSO)) throw new ForbiddenException();
        return user;
    }

    private static String texto(Map<String, Object> corpo, String chave) {
        Object v = corpo == null ? null : corpo.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    @GET
    public Map<String, Object> vigentes() {
        AuthenticatedUser user = usuario();
        return Map.of("podeAprovar", service.podeAprovar(user), "aprovacoes", service.vigentes());
    }

    @GET
    @Path("historico")
    public List<Map<String, Object>> historico(@QueryParam("protocoloId") String protocoloId) {
        usuario();
        if (ProtocoloExecucaoService.limitar(protocoloId, 80) == null) return List.of();
        return service.historico(protocoloId.trim());
    }

    // ---- Comissao: cartao "Comissão de protocolos" no cadastro de servidores (so administracao) ----

    private AuthenticatedUser administrador() {
        AuthenticatedUser user = security.resolve(request);
        if (user == null || user.matricula() == null || user.matricula().isBlank()) throw new NotAuthorizedException("Bearer");
        if (!user.hasPermission("kadm")) throw new ForbiddenException();
        return user;
    }

    @GET
    @Path("/comissao")
    public Response situacaoComissao(@QueryParam("matricula") String matricula) {
        administrador();
        try {
            return Response.ok(service.situacaoComissao(matricula)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @jakarta.ws.rs.PUT
    @Path("/comissao")
    public Response definirComissao(Map<String, Object> corpo) {
        AuthenticatedUser user = administrador();
        try {
            service.definirComissao(texto(corpo, "matricula"), Boolean.parseBoolean(texto(corpo, "membro")), user);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    public Response decidir(Map<String, Object> corpo) {
        AuthenticatedUser user = usuario();
        if (!service.podeAprovar(user)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", "Você não faz parte da comissão de aprovação de protocolos.")).build();
        }
        try {
            // Unidade de quem decide: da sessao (ou do cadastro), nunca do cliente.
            String unidade = security.unidadeDaSessao(request);
            if (unidade == null || unidade.isBlank()) unidade = user.unidade();
            service.decidir(texto(corpo, "protocoloId"), texto(corpo, "versao"), texto(corpo, "decisao"),
                    texto(corpo, "justificativa"), unidade, user);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }
}
