package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.GerarRemessaDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoRemessaService;

@Path("faturamento/exportacoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoExportacaoController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoRemessaService remessaService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    @Path("resumo")
    public Response resumo(@QueryParam("idcompetencia") Integer idcompetencia, @QueryParam("tipo") String tipo,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        try {
            return Response.ok(remessaService.resumo(idcompetencia, tipo, unidadeDaExportacao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("diagnostico")
    public Response diagnostico(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        if (idcompetencia == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Informe a competencia.").build();
        }
        return Response.ok(remessaService.diagnostico(idcompetencia, unidadeDaExportacao(u, unidade))).build();
    }

    @GET
    @Path("prontidao")
    public Response prontidao(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        if (idcompetencia == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Informe a competencia.").build();
        }
        try {
            return Response.ok(remessaService.prontidao(idcompetencia, unidadeDaExportacao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("gerar")
    public Response gerar(GerarRemessaDto dto) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("bpaexportar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            var remessa = remessaService.gerar(dto == null ? null : dto.getIdcompetencia(),
                    dto == null ? null : dto.getTipo(), u,
                    unidadeDaExportacao(u, dto == null ? null : dto.getUnidade()));
            return Response.status(Response.Status.CREATED).entity(remessa).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (UnsupportedOperationException e) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).entity(e.getMessage()).build();
        }
    }

    private String unidadeDaExportacao(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        if (unidadeSolicitada != null && !unidadeSolicitada.isBlank()) {
            return unidadeSolicitada;
        }
        return user.unidade();
    }
}
