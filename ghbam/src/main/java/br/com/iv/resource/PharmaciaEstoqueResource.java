package br.com.iv.resource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.service.PharmaciaRequestScope;
import br.com.iv.service.PharmaciaEstoqueService;

@Path("/pharmacia/estoque")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaEstoqueResource {

    @Inject
    PharmaciaEstoqueService service;
    @Inject br.com.iv.service.PharmaciaRequestScope scope;
    @jakarta.ws.rs.core.Context jakarta.ws.rs.container.ContainerRequestContext request;

    @GET
    public List<PharmaciaEstoque> listAll(@QueryParam("unidadeId") String unidadeParam) {
        if (unidadeParam != null && !unidadeParam.isEmpty()) {
            return service.findByUnidadeSigla(unidadeParam);
        }
        // Sem filtro devolvia o estoque de TODAS as unidades: agora so o da sessao.
        return service.findByUnidadeSigla(scope.unidadeAtual(request));
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaEstoque estoque = service.findById(id);
        if (estoque == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(estoque).build();
    }

    @POST
    public Response save(PharmaciaEstoque estoque) {
        if (estoque == null || estoque.getId() != null) throw new BadRequestException("Novo registro obrigatorio.");
        scope.exigirArea(request, estoque.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
        try {
            PharmaciaEstoque saved = service.save(estoque);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, PharmaciaEstoque estoque) {
        PharmaciaEstoque original = service.findById(id);
        if (original == null) throw new NotFoundException();
        if (estoque == null) throw new BadRequestException();
        scope.exigirArea(request, original.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
        scope.exigirArea(request, estoque.getUnidadeSigla(), PharmaciaRequestScope.ESTOQUE);
        try {
            estoque.setId(id);
            PharmaciaEstoque updated = service.save(estoque);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        PharmaciaEstoque original = service.findById(id);
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
    @Path("/unidade/{sigla}")
    public List<PharmaciaEstoque> findByUnidade(@PathParam("sigla") String sigla) {
        return service.findByUnidadeSigla(sigla);
    }

    @GET
    @Path("/lote/{loteId}")
    public List<PharmaciaEstoque> findByLote(@PathParam("loteId") Long loteId) {
        return service.findByLoteId(loteId);
    }

    @GET
    @Path("/lote/{loteId}/unidade/{sigla}")
    public Response findByLoteAndSigla(
            @PathParam("loteId") Long loteId,
            @PathParam("sigla") String sigla) {
        PharmaciaEstoque estoque = service.findByLoteAndSigla(loteId, sigla);
        if (estoque == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(estoque).build();
    }

    @GET
    @Path("/estoque-baixo")
    public List<PharmaciaEstoque> findEstoqueBaixo() {
        String unidade = scope.unidadeAtual(request);
        return service.findEstoqueBaixo().stream()
                .filter(e -> unidade != null && unidade.equalsIgnoreCase(e.getUnidadeSigla()))
                .toList();
    }
}
