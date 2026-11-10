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
 * Edicao de textos dos protocolos. Ler: quem usa os protocolos (o conteudo revisado vale para
 * todos). Editar: so a comissao (mesma regra da aprovacao), conferido no servidor.
 */
@Path("protocolos-fluxograma/revisoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProtocoloRevisaoController {

    @Context
    ContainerRequestContext request;

    @Inject
    SecurityPermissionService security;

    @Inject
    ProtocoloRevisaoService service;

    @Inject
    ProtocoloAprovacaoService aprovacao;

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
    public List<Map<String, Object>> vigentes() {
        usuario();
        return service.vigentes();
    }

    @GET
    @Path("historico")
    public List<Map<String, Object>> historico(@QueryParam("protocoloId") String protocoloId) {
        usuario();
        if (ProtocoloExecucaoService.limitar(protocoloId, 80) == null) return List.of();
        return service.historico(protocoloId.trim());
    }

    @POST
    public Response salvar(Map<String, Object> corpo) {
        AuthenticatedUser user = usuario();
        if (!aprovacao.podeAprovar(user)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", "Somente a comissão de protocolos pode editar.")).build();
        }
        try {
            String unidade = security.unidadeDaSessao(request);
            if (unidade == null || unidade.isBlank()) unidade = user.unidade();
            String versao = service.salvar(texto(corpo, "protocoloId"), texto(corpo, "versaoBase"),
                    corpo == null ? null : corpo.get("textos"), texto(corpo, "justificativa"), unidade, user);
            return Response.status(Response.Status.CREATED).entity(Map.of("versao", versao)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }
}
