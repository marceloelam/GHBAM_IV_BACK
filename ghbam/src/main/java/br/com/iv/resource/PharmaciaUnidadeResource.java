package br.com.iv.resource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaUnidade;
import br.com.iv.service.PharmaciaUnidadeService;

@Path("/pharmacia/unidades")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaUnidadeResource {

    @Inject
    PharmaciaUnidadeService service;

    @GET
    public List<PharmaciaUnidade> listAll() {
        return service.listAll();
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaUnidade unidade = service.findById(id);
        if (unidade == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(unidade).build();
    }

    @POST
    public Response save(PharmaciaUnidade unidade) {
        try {
            PharmaciaUnidade saved = service.save(unidade);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, PharmaciaUnidade unidade) {
        try {
            unidade.setId(id);
            PharmaciaUnidade updated = service.save(unidade);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        try {
            service.delete(id);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/ativo/{ativo}")
    public List<PharmaciaUnidade> findByAtivo(@PathParam("ativo") Boolean ativo) {
        return service.findByAtivo(ativo);
    }
}
