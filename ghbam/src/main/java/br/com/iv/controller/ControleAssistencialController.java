package br.com.iv.controller;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ControleAssistencialService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("controle-assistencial")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ControleAssistencialController {

    @Inject
    ControleAssistencialService service;

    @Context
    ContainerRequestContext requestContext;

    @GET
    public Response listar(
            @QueryParam("unidade") String unidade,
            @QueryParam("tipo") String tipo,
            @QueryParam("status") String status,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("limite") Integer limite) {
        AuthenticatedUser usuario = exigirAcesso();
        String unidadeEfetiva = unidade == null || unidade.isBlank() ? usuario.unidade() : unidade;
        return Response.ok(service.listar(
                unidadeEfetiva,
                tipo,
                status,
                parseData(inicio),
                parseData(fim),
                limite == null ? 80 : limite)).build();
    }

    @GET
    @Path("setores")
    public Response listarSetores(@QueryParam("unidade") String unidade) {
        AuthenticatedUser usuario = exigirAcesso();
        String unidadeEfetiva = unidade == null || unidade.isBlank() ? usuario.unidade() : unidade;
        return Response.ok(service.listarSetoresDaUnidade(unidadeEfetiva)).build();
    }

    @GET
    @Path("{uuid}")
    public Response buscar(@PathParam("uuid") String uuid) {
        exigirAcesso();
        var item = service.buscarPorUuid(uuid);
        if (item == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(item).build();
    }

    @POST
    public Response salvar(JsonNode dto) {
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(service.toMap(service.salvar(dto, exigirAcesso())))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
                    .build();
        }
    }

    @PUT
    @Path("{uuid}")
    public Response atualizar(@PathParam("uuid") String uuid, JsonNode dto) {
        try {
            var registro = service.atualizar(uuid, dto, exigirAcesso());
            if (registro == null) return Response.status(Response.Status.NOT_FOUND).build();
            return Response.ok(service.toMap(registro)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
                    .build();
        }
    }

    @DELETE
    @Path("{uuid}")
    public Response cancelar(@PathParam("uuid") String uuid) {
        try {
            exigirAcesso();
            var registro = service.cancelar(uuid);
            if (registro == null) return Response.status(Response.Status.NOT_FOUND).build();
            return Response.ok(service.toMap(registro)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
                    .build();
        }
    }

    @POST
    @Path("{uuid}/impressao")
    public Response registrarImpressao(@PathParam("uuid") String uuid) {
        exigirAcesso();
        var item = service.buscarPorUuid(uuid);
        if (item == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok("{\"ok\":true}").build();
    }

    private AuthenticatedUser exigirAcesso() {
        Object usuario = requestContext.getProperty("ghbam.user");
        if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        if (!autenticado.hasAnyPermission(List.of("kenfermagem", "pcontroleassistencial", "kadm"))) {
            throw new jakarta.ws.rs.ForbiddenException("Permissao insuficiente para acessar Controle Assistencial.");
        }
        return autenticado;
    }

    private LocalDate parseData(String data) {
        try {
            return data == null || data.isBlank() ? null : LocalDate.parse(data.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
