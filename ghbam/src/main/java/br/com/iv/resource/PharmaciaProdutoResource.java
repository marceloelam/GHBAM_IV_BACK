package br.com.iv.resource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.dto.PharmaciaProdutoDTO;
import br.com.iv.entity.PharmaciaProduto;
import br.com.iv.service.PharmaciaCatalogoService;
import br.com.iv.service.PharmaciaProdutoService;
import br.com.iv.service.PharmaciaRequestScope;

// Cada unidade tem o proprio catalogo: leitura, cadastro, edicao e exclusao
// ficam restritas a unidade da sessao, independente do que o cliente enviar.
@Path("/pharmacia/produtos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaProdutoResource {

    @Inject
    PharmaciaProdutoService service;
    @Inject PharmaciaRequestScope scope;
    @Inject PharmaciaCatalogoService catalogo;
    @Context ContainerRequestContext requestContext;

    @GET
    public List<PharmaciaProduto> listAll(@QueryParam("unidadeId") String unidadeParam) {
        String unidade = scope.unidadeAtual(requestContext);
        if (unidadeParam != null && !unidadeParam.isBlank() && !unidadeParam.trim().equals(unidade)) {
            throw new ForbiddenException("O catalogo consultado deve ser o da unidade da sessao.");
        }
        return service.findByUnidadeSigla(unidade);
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaProduto produto = daUnidade(service.findById(id), scope.unidadeAtual(requestContext));
        if (produto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(produto).build();
    }

    @POST
    public Response save(PharmaciaProdutoDTO dto) {
        if (dto == null) throw new BadRequestException("Produto obrigatorio.");
        String unidade = scope.unidadeAtual(requestContext);
        scope.exigirArea(requestContext, unidade, PharmaciaRequestScope.PRODUTOS);
        dto.setId(null);
        dto.setUnidadeSigla(unidade);
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
        if (dto == null) throw new BadRequestException("Produto obrigatorio.");
        String unidade = scope.unidadeAtual(requestContext);
        scope.exigirArea(requestContext, unidade, PharmaciaRequestScope.PRODUTOS);
        if (daUnidade(service.findById(id), unidade) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            dto.setId(id);
            dto.setUnidadeSigla(unidade);
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
        String unidade = scope.unidadeAtual(requestContext);
        scope.exigirArea(requestContext, unidade, PharmaciaRequestScope.PRODUTOS);
        if (daUnidade(service.findById(id), unidade) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
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
        return daUnidade(service.findByCategoria(categoriaId), scope.unidadeAtual(requestContext));
    }

    @GET
    @Path("/ativo/{ativo}")
    public List<PharmaciaProduto> findByAtivo(@PathParam("ativo") Boolean ativo) {
        return daUnidade(service.findByAtivo(ativo), scope.unidadeAtual(requestContext));
    }

    // Catalogos de outras unidades: so a farmacia da unidade da sessao consulta e importa.
    @GET
    @Path("/catalogos")
    public List<Map<String, Object>> catalogos() {
        return catalogo.catalogos(unidadeComPermissao());
    }

    @GET
    @Path("/catalogos/{origem}")
    public Response visualizarCatalogo(@PathParam("origem") String origem) {
        String unidade = unidadeComPermissao();
        try {
            return Response.ok(catalogo.visualizar(origem, unidade)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", e.getMessage())).build();
        }
    }

    @POST
    @Path("/importar")
    public Response importarCatalogo(Map<String, Object> corpo) {
        String unidade = unidadeComPermissao();
        try {
            Object ids = corpo == null ? null : corpo.get("ids");
            List<Long> lista = ids instanceof List<?> l ? l.stream().map(v -> Long.valueOf(String.valueOf(v))).toList() : List.of();
            return Response.ok(catalogo.importar(corpo == null ? null : (String) corpo.get("origem"), unidade, lista)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", e.getMessage())).build();
        }
    }

    @POST
    @Path("/importar-planilha")
    @SuppressWarnings("unchecked")
    public Response importarPlanilha(@QueryParam("simular") boolean simular, Map<String, Object> corpo) {
        String unidade = unidadeComPermissao();
        try {
            Object linhas = corpo == null ? null : corpo.get("linhas");
            List<Map<String, Object>> lista = linhas instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            return Response.ok(catalogo.importarPlanilha(unidade, lista, simular)).build();
        } catch (IllegalArgumentException | ClassCastException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", e.getMessage())).build();
        }
    }

    private String unidadeComPermissao() {
        String unidade = scope.unidadeAtual(requestContext);
        scope.exigirArea(requestContext, unidade, PharmaciaRequestScope.PRODUTOS);
        return unidade;
    }

    static PharmaciaProduto daUnidade(PharmaciaProduto produto, String unidade) {
        return produto != null && unidade != null && unidade.equals(produto.getUnidadeSigla()) ? produto : null;
    }

    static List<PharmaciaProduto> daUnidade(List<PharmaciaProduto> produtos, String unidade) {
        return produtos.stream().filter(p -> daUnidade(p, unidade) != null).collect(Collectors.toList());
    }
}
