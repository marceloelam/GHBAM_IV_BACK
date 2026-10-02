package br.com.iv.controller;

import java.util.Map;

import br.com.iv.service.OcorrenciaSistemaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("ocorrencia-sistema")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OcorrenciaSistemaController {

    @Inject
    OcorrenciaSistemaService service;

    @GET
    public Response listar(
            @QueryParam("unidade") String unidade,
            @QueryParam("status") String status,
            @QueryParam("tipo") String tipo,
            @QueryParam("busca") String busca) {
        return Response.ok(service.listar(unidade, status, tipo, busca)).build();
    }

    @POST
    public Response salvar(Map<String, Object> dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Payload ausente")).build();
        }
        return Response.status(Response.Status.CREATED).entity(service.salvar(dto)).build();
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") int id, Map<String, Object> dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Payload ausente")).build();
        }
        return Response.ok(service.atualizar(id, dto)).build();
    }

    @PUT
    @Path("{id}/status")
    public Response alterarStatus(@PathParam("id") int id, Map<String, Object> dto) {
        return Response.ok(service.alterarStatus(id, dto)).build();
    }

    @DELETE
    @Path("{id}")
    public Response excluir(@PathParam("id") int id) {
        service.excluir(id);
        return Response.noContent().build();
    }

    @GET
    @Path("alerta-config")
    public Response obterAlertaConfig(@QueryParam("unidade") String unidade) {
        return Response.ok(service.obterAlertaConfig(unidade)).build();
    }

    @PUT
    @Path("alerta-config")
    public Response salvarAlertaConfig(Map<String, Object> dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Payload ausente")).build();
        }
        return Response.ok(service.salvarAlertaConfig(dto)).build();
    }
}
