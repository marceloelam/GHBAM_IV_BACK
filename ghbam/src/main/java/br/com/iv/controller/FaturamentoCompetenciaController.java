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

import br.com.iv.dto.AbrirCompetenciaDto;
import br.com.iv.dto.ReabrirCompetenciaDto;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoCompetenciaService;

@Path("faturamento/competencias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoCompetenciaController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoCompetenciaService competenciaService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    public List<FaturamentoCompetencia> listar(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return competenciaService.listarTodas(unidadeDaCompetencia(u, unidade));
    }

    @POST
    public Response abrir(AbrirCompetenciaDto dto, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        try {
            FaturamentoCompetencia competencia = competenciaService.abrir(dto == null ? null : dto.getCompetencia(), u,
                    unidadeDaCompetencia(u, unidade));
            return Response.status(Response.Status.CREATED).entity(competencia).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("fechar/{id}")
    public Response fechar(@PathParam("id") int id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("pfaturamentosusfechar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(competenciaService.fechar(id, u, unidadeDaCompetencia(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("retirar/{id}")
    public Response retirar(@PathParam("id") int id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        try {
            competenciaService.retirar(id, u, unidadeDaCompetencia(u, unidade));
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    // Reabertura e sempre restrita a uma permissao propria (nunca ao kfaturamento
    // generico) - secao 6/28 do escopo trata isso como acao especialmente sensivel.
    @POST
    @Path("reabrir/{id}")
    public Response reabrir(@PathParam("id") int id, ReabrirCompetenciaDto dto, @QueryParam("unidade") String unidade) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser auth) || !auth.hasAnyPermission(List.of("pfaturamentosusreabrir", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(competenciaService.reabrir(id, dto == null ? null : dto.getMotivo(), auth,
                    unidadeDaCompetencia(auth, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    private String unidadeDaCompetencia(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        if (unidadeSolicitada != null && !unidadeSolicitada.isBlank()) {
            return unidadeSolicitada;
        }
        return user.unidade();
    }
}
