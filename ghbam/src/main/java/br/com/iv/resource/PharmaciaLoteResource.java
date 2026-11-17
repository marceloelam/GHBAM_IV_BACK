package br.com.iv.resource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaLote;
import br.com.iv.service.PharmaciaRequestScope;
import br.com.iv.service.PharmaciaLoteService;

@Path("/pharmacia/lotes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaLoteResource {

    @Inject
    PharmaciaLoteService service;
    @Inject br.com.iv.service.PharmaciaRequestScope scope;
    @jakarta.ws.rs.core.Context jakarta.ws.rs.container.ContainerRequestContext request;

    @GET
    public List<PharmaciaLote> listAll(@QueryParam("unidadeId") String unidadeParam) {
        // Com unidadeId: a permuta consulta os lotes da unidade FORNECEDORA (leitura liberada).
        if (unidadeParam != null && !unidadeParam.isEmpty()) {
            return service.findByUnidadeSigla(unidadeParam);
        }
        // Sem filtro devolvia lotes de TODAS as unidades: agora so os da sessao.
        return service.findByUnidadeSigla(scope.unidadeAtual(request));
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
        if (lote == null || lote.getId() != null) throw new BadRequestException("Novo registro obrigatorio.");
        scope.exigirArea(request, lote.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
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
        PharmaciaLote original = service.findById(id);
        if (original == null) throw new NotFoundException();
        if (lote == null) throw new BadRequestException();
        scope.exigirArea(request, original.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
        scope.exigirArea(request, lote.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
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
        PharmaciaLote original = service.findById(id);
        if (original == null) throw new NotFoundException();
        scope.exigirArea(request, original.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
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
