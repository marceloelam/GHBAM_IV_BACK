package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.service.DiagnosticoSistemaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("diagnostico-sistema")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DiagnosticoSistemaController {

    @Inject
    DiagnosticoSistemaService service;

    @GET
    public Response listar(
            @QueryParam("unidade") String unidade,
            @QueryParam("tipo") String tipo,
            @QueryParam("modulo") String modulo,
            @QueryParam("severidade") String severidade,
            @QueryParam("busca") String busca,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim) {
        return Response.ok(service.listar(unidade, tipo, modulo, severidade, busca, inicio, fim)).build();
    }

    @POST
    public Response salvar(Map<String, Object> dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Payload ausente")).build();
        }
        return Response.status(Response.Status.CREATED).entity(service.salvar(dto)).build();
    }

    @POST
    @Path("lote")
    public Response salvarLote(List<Map<String, Object>> itens) {
        return Response.ok(Map.of("total", service.salvarLote(itens))).build();
    }
}
