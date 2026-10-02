package br.com.iv.resource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaLote;
import br.com.iv.service.PharmaciaLoteService;

@Path("/pharmacia/lotes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaLoteResource {

    @Inject
    PharmaciaLoteService service;

    @GET
    public List<PharmaciaLote> listAll(@QueryParam("unidadeId") String unidadeParam) {
        if (unidadeParam != null && !unidadeParam.isEmpty()) {
            return service.findByUnidadeSigla(unidadeParam);
        }
        return service.listAll();
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaLote lote = service.findById(id);
        if (lote == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(lote).build();
    }

    @POST
    public Response save(PharmaciaLote lote) {
        try {
            PharmaciaLote saved = service.save(lote);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, PharmaciaLote lote) {
        try {
            lote.setId(id);
            PharmaciaLote updated = service.save(lote);
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
    @Path("/produto/{produtoId}")
    public List<PharmaciaLote> findByProduto(@PathParam("produtoId") Long produtoId) {
        return service.findByProduto(produtoId);
    }

    @GET
    @Path("/vencendo/{dias}")
    public List<PharmaciaLote> findVencendo(@PathParam("dias") int dias) {
        return service.findVencendoEm(dias);
    }

    @GET
    @Path("/vencidos")
    public List<PharmaciaLote> findVencidos() {
        return service.findVencidos();
    }

    @PUT
    @Path("/bloquear/{id}")
    public Response bloquearLote(@PathParam("id") Long id) {
        try {
            service.bloquearLoteVencido(id);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
