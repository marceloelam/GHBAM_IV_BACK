package br.com.iv.controller;

import java.util.List;
import java.util.Map;

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

import br.com.iv.dto.CriarRaasDto;
import br.com.iv.entity.FaturamentoRaas;
import br.com.iv.enums.RaasStatus;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoRaasService;

@Path("faturamento/raas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoRaasController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoRaasService raasService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    public Object listar(@QueryParam("status") String status, @QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade, @QueryParam("q") String busca,
            @QueryParam("pagina") Integer pagina, @QueryParam("tamanho") Integer tamanho) {
        AuthenticatedUser u = user();
        String unidadeEfetiva = unidadeDoUsuario(u, unidade);
        if (pagina != null || tamanho != null || unidadeEfetiva != null || busca != null) {
            int pag = pagina == null ? 0 : Math.max(pagina, 0);
            int tam = tamanho == null ? 50 : tamanho;
            List<FaturamentoRaas> itens = raasService.listarPaginado(status, idcompetencia, unidadeEfetiva, busca, pag, tam);
            long total = raasService.contarFiltrado(status, idcompetencia, unidadeEfetiva, busca);
            return Map.of("itens", itens, "total", total, "pagina", pag, "tamanho", tam <= 0 || tam > 200 ? 50 : tam);
        }
        return raasService.listar(status, idcompetencia, unidadeEfetiva);
    }

    @POST
    public Response criar(CriarRaasDto dto) {
        AuthenticatedUser u = user();
        try {
            if (dto != null && (dto.getUnidade() == null || dto.getUnidade().isBlank())) {
                dto.setUnidade(unidadeDoUsuario(u, null));
            }
            return Response.status(Response.Status.CREATED).entity(raasService.criar(dto, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("concluir/{id}")
    public Response concluir(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        try {
            return Response.ok(raasService.alterarStatus(id, RaasStatus.CONCLUIDA, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("cancelar/{id}")
    public Response cancelar(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        try {
            return Response.ok(raasService.alterarStatus(id, RaasStatus.CANCELADA, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
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
