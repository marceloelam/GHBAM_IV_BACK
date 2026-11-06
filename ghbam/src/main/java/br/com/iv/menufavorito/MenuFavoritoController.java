package br.com.iv.menufavorito;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Favoritos do menu do proprio usuario. A matricula vem sempre do token, nunca do cliente:
 * ninguem le nem altera os favoritos de outra pessoa. Basta estar autenticado
 * (AUTHENTICATED_ONLY_PATHS do RbacRequestFilter), porque favorito nao concede acesso a tela.
 */
@Path("api/menu-favoritos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MenuFavoritoController {

    @Context
    ContainerRequestContext context;

    @Inject
    MenuFavoritoService service;

    private String matricula() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || a.matricula() == null || a.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return a.matricula().trim();
    }

    private static Response caminhoInvalido() {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Tela inválida.")).build();
    }

    @GET
    public List<String> listar() {
        return service.listar(matricula());
    }

    @POST
    public Response adicionar(Map<String, String> corpo) {
        String matricula = matricula();
        String caminho = corpo == null ? null : corpo.get("caminho");
        if (!MenuFavoritoService.caminhoValido(caminho)) return caminhoInvalido();
        try {
            service.adicionar(matricula, caminho);
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @DELETE
    public Response remover(@QueryParam("caminho") String caminho) {
        String matricula = matricula();
        if (!MenuFavoritoService.caminhoValido(caminho)) return caminhoInvalido();
        service.remover(matricula, caminho);
        return Response.noContent().build();
    }
}
