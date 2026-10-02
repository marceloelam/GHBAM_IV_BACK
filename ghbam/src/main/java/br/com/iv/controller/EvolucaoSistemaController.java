package br.com.iv.controller;

import java.util.Map;

import br.com.iv.service.EvolucaoSistemaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("evolucao-sistema")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvolucaoSistemaController {

    @Inject
    EvolucaoSistemaService service;

    @GET
    public Response listar(
            @QueryParam("unidade") String unidade,
            @QueryParam("modulo") String modulo,
            @QueryParam("tipo") String tipo,
            @QueryParam("busca") String busca) {
        return Response.ok(service.listar(unidade, modulo, tipo, busca)).build();
    }

    @POST
    public Response salvar(Map<String, Object> dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Payload ausente")).build();
        }
        return Response.status(Response.Status.CREATED).entity(service.salvar(dto)).build();
    }

    @DELETE
    @Path("{id}")
    public Response excluir(@PathParam("id") int id) {
        service.excluir(id);
        return Response.noContent().build();
    }
}
