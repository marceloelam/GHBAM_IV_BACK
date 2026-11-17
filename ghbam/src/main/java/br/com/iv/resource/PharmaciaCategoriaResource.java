package br.com.iv.resource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaCategoria;
import br.com.iv.service.PharmaciaCategoriaService;
import br.com.iv.service.PharmaciaRequestScope;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@Path("/pharmacia/categorias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaCategoriaResource {

    @Inject
    PharmaciaCategoriaService service;
    @Inject PharmaciaRequestScope scope;
    @Context ContainerRequestContext requestContext;

    // Categorias sao cadastro da farmacia: gravar exige a area Produtos.
    private void exigirProdutos() {
        scope.exigirArea(requestContext, scope.unidadeAtual(requestContext), PharmaciaRequestScope.PRODUTOS);
    }

    @GET
    public List<PharmaciaCategoria> listAll(@QueryParam("unidadeId") String unidadeParam) {
        if (unidadeParam != null && !unidadeParam.isEmpty()) {
            return service.findByUnidadeSigla(unidadeParam);
        }
        // Sem filtro devolvia categorias de TODAS as unidades: agora so as da sessao.
        return service.findByUnidadeSigla(scope.unidadeAtual(requestContext));
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaCategoria categoria = service.findById(id);
        if (categoria == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(categoria).build();
    }

    @POST
    public Response save(PharmaciaCategoria categoria) {
        exigirProdutos();
        try {
            PharmaciaCategoria saved = service.save(categoria);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, PharmaciaCategoria categoria) {
        exigirProdutos();
        try {
            categoria.setId(id);
            PharmaciaCategoria updated = service.save(categoria);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}/desativar")
    public Response desativar(@PathParam("id") Long id) {
        exigirProdutos();
        try {
            service.desativar(id);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/ativo/{ativo}")
    public List<PharmaciaCategoria> findByAtivo(@PathParam("ativo") Boolean ativo) {
        return service.findByAtivo(ativo);
    }
}
