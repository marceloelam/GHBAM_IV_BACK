package br.com.iv.resource;

import br.com.iv.dto.PharmaciaOperacaoRequest;
import br.com.iv.service.PharmaciaOperacaoService;
import br.com.iv.service.PharmaciaRequestScope;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Map;

@Path("/pharmacia/operacoes-unidade")
@Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaOperacaoResource {
    @Inject PharmaciaOperacaoService service;
    @Inject PharmaciaRequestScope scope;
    @Context ContainerRequestContext request;

    @GET public Object listar(@QueryParam("unidade") String unidade) { scope.exigirUnidade(request, unidade); return service.listar(unidade); }
    @GET @Path("/resumo") public Object resumo(@QueryParam("unidade") String unidade) { scope.exigirUnidade(request, unidade); return service.resumo(unidade); }
    @GET @Path("/condicoes") public Object condicoes(@QueryParam("unidade") String unidade) { scope.exigirUnidade(request, unidade); return service.condicoes(unidade); }
    @GET @Path("/rastrear/{lote}") public Object rastrear(@PathParam("lote") Long lote, @QueryParam("unidade") String unidade) {
        scope.exigirUnidade(request, unidade); try { return service.rastrear(lote, unidade); } catch (IllegalArgumentException e) { throw new BadRequestException(e.getMessage()); }
    }
    @GET @Path("/{id}/documento") @Produces("application/pdf")
    public Response documento(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        String matricula = scope.exigirUnidade(request, unidade).matricula();
        return Response.ok(service.documento(id, unidade, matricula)).type("application/pdf")
            .header("Content-Disposition", "inline; filename=\"documento-farmacia.pdf\"")
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").build();
    }
    @POST public Response criar(@QueryParam("unidade") String unidade, PharmaciaOperacaoRequest body) {
        String matricula = scope.exigirArea(request, unidade, PharmaciaRequestScope.OPERACOES).matricula();
        try { return Response.status(201).entity(service.criar(body, unidade, matricula)).build(); } catch (IllegalArgumentException e) { throw new BadRequestException(e.getMessage()); }
    }
    @POST @Path("/{id}/avaliar") public Object avaliar(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, String> body) {
        String matricula = scope.exigirArea(request, unidade, PharmaciaRequestScope.AVALIACAO).matricula();
        if (body == null) throw new BadRequestException("Informe a avaliacao.");
        try { return service.avaliar(id, unidade, matricula, body.get("decisao"), body.get("parecer"), body.get("documento")); } catch (IllegalArgumentException e) { throw new BadRequestException(e.getMessage()); }
    }
}
