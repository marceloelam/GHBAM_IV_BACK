package br.com.iv.passkey;

import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.SessaoJaAtivaException;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;

@Path("/auth/passkeys")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PasskeyAuthResource {
    @Inject
    PasskeyAuthService passkeyAuthService;

    @Inject
    SecurityPermissionService securityPermissionService;

    @POST
    @Path("/registration/options")
    public Response registrationOptions(Map<String, Object> body, @Context ContainerRequestContext requestContext) {
        AuthenticatedUser user = securityPermissionService.resolve(requestContext);
        return Response.ok(passkeyAuthService.iniciarRegistro(user, requestContext.getHeaderString("Origin"))).build();
    }

    @POST
    @Path("/registration/finish")
    public Response registrationFinish(Map<String, Object> body, @Context ContainerRequestContext requestContext) {
        AuthenticatedUser user = securityPermissionService.resolve(requestContext);
        try {
            return Response.ok(passkeyAuthService.finalizarRegistro(user, body, requestContext.getHeaderString("Origin"))).build();
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @GET
    @Path("/current-device")
    public Response currentDevice(@Context ContainerRequestContext requestContext) {
        AuthenticatedUser user = securityPermissionService.resolve(requestContext);
        return Response.ok(passkeyAuthService.statusDispositivo(user)).build();
    }

    @DELETE
    @Path("/current-device")
    public Response disableCurrentDevice(@Context ContainerRequestContext requestContext) {
        AuthenticatedUser user = securityPermissionService.resolve(requestContext);
        return Response.ok(passkeyAuthService.desativarDispositivo(user)).build();
    }

    @POST
    @Path("/authentication/options")
    public Response authenticationOptions(Map<String, Object> body, @Context ContainerRequestContext requestContext) {
        return Response.ok(passkeyAuthService.iniciarLogin(requestContext.getHeaderString("Origin"), body)).build();
    }

    @POST
    @Path("/authentication/finish")
    public Response authenticationFinish(Map<String, Object> body, @Context HttpHeaders headers) {
        try {
            return Response.ok(passkeyAuthService.finalizarLogin(body, headers)).build();
        } catch (SessaoJaAtivaException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of(
                    "erro", "USUARIO_JA_LOGADO",
                    "mensagem", "Esta matricula ja possui sessao ativa em outro dispositivo.",
                    "matricula", e.getSessao() == null ? "" : e.getSessao().getMatricula(),
                    "computador", e.getSessao() == null ? "" : e.getSessao().getComputador()
                ))
                .build();
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    private Response badRequest(BadRequestException e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "erro", "PASSKEY_INVALIDA",
                "mensagem", e.getMessage() == null || e.getMessage().isBlank()
                    ? "Nao foi possivel validar a biometria deste celular."
                    : e.getMessage()
            ))
            .build();
    }
}
