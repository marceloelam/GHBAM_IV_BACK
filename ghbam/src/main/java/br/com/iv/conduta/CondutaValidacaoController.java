package br.com.iv.conduta;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Validação das condutas clínicas do Bulário.
 * - GET: qualquer usuário autenticado (a tela mostra quem validou); diz também se ele pode validar.
 * - POST/DELETE: só quem passa em CondutaValidacaoService.podeValidar (CRM + kadm/coordenador).
 * Rota em AUTHENTICATED_ONLY_PATHS do RbacRequestFilter; a regra fina fica aqui.
 */
@Path("api/conduta-validacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CondutaValidacaoController {

    @Context
    ContainerRequestContext context;

    @Inject
    CondutaValidacaoService service;

    private AuthenticatedUser usuario() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || a.matricula() == null || a.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return a;
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).entity(Map.of("erro", mensagem)).build();
    }

    @GET
    public Response listar() {
        AuthenticatedUser u = usuario();
        return Response.ok(Map.of(
                "podeValidar", CondutaValidacaoService.podeValidar(u),
                "validacoes", service.ativas())).build();
    }

    @POST
    public Response validar(Map<String, String> corpo) {
        AuthenticatedUser u = usuario();
        if (!CondutaValidacaoService.podeValidar(u)) {
            return erro(Response.Status.FORBIDDEN, CondutaValidacaoService.motivoSemPermissao(u));
        }
        String uuid = corpo == null ? null : corpo.get("uuid");
        String hash = corpo == null ? null : corpo.get("hash");
        if (!CondutaValidacaoService.uuidValido(uuid)) return erro(Response.Status.BAD_REQUEST, "Conduta inválida.");
        if (!CondutaValidacaoService.hashValido(hash)) return erro(Response.Status.BAD_REQUEST, "Conteúdo da conduta inválido.");
        service.validar(u, uuid, hash, corpo.get("titulo"));
        return Response.noContent().build();
    }

    @DELETE
    @Path("{uuid}")
    public Response revogar(@PathParam("uuid") String uuid) {
        AuthenticatedUser u = usuario();
        if (!CondutaValidacaoService.podeValidar(u)) {
            return erro(Response.Status.FORBIDDEN, CondutaValidacaoService.motivoSemPermissao(u));
        }
        if (!CondutaValidacaoService.uuidValido(uuid)) return erro(Response.Status.BAD_REQUEST, "Conduta inválida.");
        service.revogar(u, uuid);
        return Response.noContent().build();
    }
}
