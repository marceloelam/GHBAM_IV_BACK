
package br.com.iv.controller;

import java.util.List;

import br.com.iv.entity.Violencia;
import br.com.iv.service.ViolenciaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/violencias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)

public class ViolenciaController {

    @Inject
    ViolenciaService service;

    @GET
    @Path("/bam/{bam}")
    public Response findByBam(@PathParam("bam") int bam) {
        List<Violencia> lista = service.findByBam(bam);
        return Response.ok(lista).build();
    }

    @POST
    public Response create(Violencia violencia) {
        if (violencia.getTipo() == null || violencia.getTipo().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Campo 'tipo' é obrigatório.").build();
        }
        Violencia saved = service.save(violencia);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @GET
    public List<Violencia> list() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return service.findById(id)
                .map(v -> Response.ok(v).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, Violencia violencia) {
        return service.findById(id).map(existing -> {
            violencia.setId(existing.getId());
            Violencia updated = service.save(violencia);
            return Response.ok(updated).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteById(id);
        return Response.noContent().build();
    }
}
