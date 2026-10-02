package br.com.iv.resource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.dto.PharmaciaProdutoDTO;
import br.com.iv.entity.PharmaciaProduto;
import br.com.iv.service.PharmaciaProdutoService;

@Path("/pharmacia/produtos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaProdutoResource {

    @Inject
    PharmaciaProdutoService service;

    @GET
    public List<PharmaciaProduto> listAll(@QueryParam("unidadeId") String unidadeParam) {
        if (unidadeParam != null && !unidadeParam.isEmpty()) {
            return service.findByUnidadeSigla(unidadeParam);
        }
        return service.listAll();
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaProduto produto = service.findById(id);
        if (produto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(produto).build();
    }

    @POST
    public Response save(PharmaciaProdutoDTO dto) {
        try {
            PharmaciaProduto saved = service.saveFromDTO(dto);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, PharmaciaProdutoDTO dto) {
        try {
            dto.setId(id);
            PharmaciaProduto updated = service.saveFromDTO(dto);
            return Response.ok(updated).build();
        } catch (Exception e) {
            e.printStackTrace();
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
    @Path("/categoria/{categoriaId}")
    public List<PharmaciaProduto> findByCategoria(@PathParam("categoriaId") Long categoriaId) {
        return service.findByCategoria(categoriaId);
    }

    @GET
    @Path("/ativo/{ativo}")
    public List<PharmaciaProduto> findByAtivo(@PathParam("ativo") Boolean ativo) {
        return service.findByAtivo(ativo);
    }
}
