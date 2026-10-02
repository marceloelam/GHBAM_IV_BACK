package br.com.iv.controller;

import java.util.Map;

import br.com.iv.service.ConfiguracaoConformidadeSlaService;
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

@Path("configuracao-conformidade-sla")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConfiguracaoConformidadeSlaController {

    @Inject
    ConfiguracaoConformidadeSlaService service;

    @GET
    public Response listar(
            @QueryParam("unidade") String unidade,
            @QueryParam("ativos") String ativos) {
        boolean apenasAtivos = ativos == null || !"false".equalsIgnoreCase(ativos);
        return Response.ok(service.listar(unidade, apenasAtivos)).build();
    }

    @GET
    @Path("historico")
    public Response listarHistorico(@QueryParam("unidade") String unidade) {
        return Response.ok(service.listarHistorico(unidade)).build();
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
    @Path("{id}/ativo")
    public Response alterarAtivo(@PathParam("id") int id, Map<String, Object> dto) {
        return Response.ok(service.alterarAtivo(id, dto)).build();
    }

    @DELETE
    @Path("{id}")
    public Response excluir(@PathParam("id") int id, @QueryParam("usuario") String usuario) {
        service.excluir(id, usuario);
        return Response.noContent().build();
    }
}
