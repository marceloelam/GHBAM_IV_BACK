package br.com.iv.controller;

import br.com.iv.dto.FhirEnvioDestinoDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FhirEnvioDestinoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("interoperabilidade/fhir/destino")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FhirEnvioDestinoController {

    @Inject
    FhirEnvioDestinoService service;

    @GET
    @Path("/{unidade}")
    public Response buscar(@PathParam("unidade") String unidade, @QueryParam("finalidade") String finalidade) {
        return service.buscar(unidade, finalidade)
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).entity("Destino de envio FHIR nao configurado.").build());
    }

    @POST
    public Response salvar(FhirEnvioDestinoDto dto, @Context ContainerRequestContext context) {
        try {
            return Response.ok(service.salvar(dto, user(context))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    private AuthenticatedUser user(ContainerRequestContext context) {
        Object value = context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : AuthenticatedUser.empty();
    }
}
