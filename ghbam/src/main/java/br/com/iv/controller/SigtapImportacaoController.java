package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

import br.com.iv.dto.SigtapImportacaoRequestDto;
import br.com.iv.dto.SigtapImportarDatasusRequestDto;
import br.com.iv.entity.FaturamentoSigtapImportacao;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.SigtapImportacaoService;

@Path("sigtap")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SigtapImportacaoController {

    @Context
    ContainerRequestContext context;

    @Inject
    SigtapImportacaoService sigtapImportacaoService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    @Path("competencias")
    public List<FaturamentoSigtapImportacao> competenciasInstaladas(@QueryParam("unidade") String unidade) {
        user();
        return sigtapImportacaoService.listarCompetenciasInstaladas(unidade);
    }

    @POST
    @Path("importar")
    public Response importar(@QueryParam("unidade") String unidade, SigtapImportacaoRequestDto dto) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("sigtapimportar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(sigtapImportacaoService.importar(dto, u, unidade)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("competencias/{id}")
    public Response removerCompetencia(@PathParam("id") int id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("sigtapimportar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(sigtapImportacaoService.removerCompetencia(id, u, unidade)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("datasus/competencias")
    public Response competenciasDatasus() {
        user();
        try {
            return Response.ok(sigtapImportacaoService.listarCompetenciasDatasus()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("importar-datasus")
    public Response importarDatasus(@QueryParam("unidade") String unidade, SigtapImportarDatasusRequestDto dto) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("sigtapimportar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(sigtapImportacaoService.importarDatasus(dto, u, unidade)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(e.getMessage()).build();
        }
    }
}
