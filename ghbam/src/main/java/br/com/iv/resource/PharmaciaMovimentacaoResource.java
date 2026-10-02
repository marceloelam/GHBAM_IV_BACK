package br.com.iv.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaMovimentacao;
import br.com.iv.service.PharmaciaMovimentacaoService;

@Path("/pharmacia/movimentacoes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaMovimentacaoResource {

    @Inject
    PharmaciaMovimentacaoService service;

    @GET
    public List<PharmaciaMovimentacao> listAll(@QueryParam("unidadeId") String unidadeParam) {
        if (unidadeParam != null && !unidadeParam.isEmpty()) {
            List<PharmaciaMovimentacao> origem = service.findByUnidadeOrigemSigla(unidadeParam);
            List<PharmaciaMovimentacao> destino = service.findByUnidadeDestinoSigla(unidadeParam);
            Set<PharmaciaMovimentacao> combined = new HashSet<>(origem);
            combined.addAll(destino);
            return new ArrayList<>(combined);
        }
        return service.listAll();
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaMovimentacao movimentacao = service.findById(id);
        if (movimentacao == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(movimentacao).build();
    }

    @POST
    public Response save(PharmaciaMovimentacao movimentacao) {
        try {
            PharmaciaMovimentacao saved = service.save(movimentacao);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, PharmaciaMovimentacao movimentacao) {
        try {
            movimentacao.setId(id);
            PharmaciaMovimentacao updated = service.save(movimentacao);
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
    @Path("/unidade-origem/{sigla}")
    public List<PharmaciaMovimentacao> findByUnidadeOrigem(@PathParam("sigla") String sigla) {
        return service.findByUnidadeOrigemSigla(sigla);
    }

    @GET
    @Path("/unidade-destino/{sigla}")
    public List<PharmaciaMovimentacao> findByUnidadeDestino(@PathParam("sigla") String sigla) {
        return service.findByUnidadeDestinoSigla(sigla);
    }

    @GET
    @Path("/lote/{loteId}")
    public List<PharmaciaMovimentacao> findByLote(@PathParam("loteId") Long loteId) {
        return service.findByLote(loteId);
    }
}
