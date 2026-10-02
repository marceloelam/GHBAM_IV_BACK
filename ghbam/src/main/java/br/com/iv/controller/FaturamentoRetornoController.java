package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.ImportarRetornoDto;
import br.com.iv.entity.FaturamentoRetorno;
import br.com.iv.entity.FaturamentoRetornoItem;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoRetornoService;

@Path("faturamento/retornos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoRetornoController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoRetornoService retornoService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    public List<FaturamentoRetorno> listar(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return retornoService.listar(unidadeDoUsuario(u, unidade));
    }

    @GET
    @Path("itens/{id}")
    public List<FaturamentoRetornoItem> listarItens(@PathParam("id") int id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return retornoService.listarItens(id, unidadeDoUsuario(u, unidade));
    }

    @POST
    @Path("importar")
    public Response importar(ImportarRetornoDto dto) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("faturamentosusimportarretorno", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            if (dto != null && (dto.getUnidade() == null || dto.getUnidade().isBlank())) {
                dto.setUnidade(unidadeDoUsuario(u, null));
            }
            return Response.status(Response.Status.CREATED).entity(retornoService.importar(dto, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    private String unidadeDoUsuario(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        if (unidadeSolicitada != null && !unidadeSolicitada.isBlank()) {
            return unidadeSolicitada;
        }
        return user.unidade();
    }
}
