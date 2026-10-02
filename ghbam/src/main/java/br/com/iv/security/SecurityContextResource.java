package br.com.iv.security;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("seguranca")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SecurityContextResource {

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    RoutePermissionCatalog routePermissionCatalog;

    @GET
    @Path("contexto")
    public Response contexto(@Context ContainerRequestContext requestContext, @QueryParam("matricula") String matricula) {
        AuthenticatedUser user = notBlank(matricula)
                ? securityPermissionService.findByMatricula(matricula)
                : securityPermissionService.resolve(requestContext);

        if (user.matricula().isBlank()) {
            Map<String, Object> unauthenticated = new LinkedHashMap<>();
            unauthenticated.put("autenticado", false);
            unauthenticated.put("fonte", "backend-db");
            unauthenticated.put("observacao", "Informe Authorization: Bearer <token>.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(unauthenticated).build();
        }

        Map<String, Object> body = user.toResponseMap();
        body.put("rotasMapeadas", routePermissionCatalog.all());
        body.put("observacao", "Permissoes calculadas pelo backend a partir das tabelas servidor/servidor2.");
        return Response.ok(body).build();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
