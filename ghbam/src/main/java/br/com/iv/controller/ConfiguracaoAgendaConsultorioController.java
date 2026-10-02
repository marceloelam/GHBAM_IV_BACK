package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.dto.ConfiguracaoAgendaConsultorioDto;
import br.com.iv.service.ConfiguracaoAgendaConsultorioService;
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

@Path("configuracaoagendaconsultorio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConfiguracaoAgendaConsultorioController {

    @Inject
    ConfiguracaoAgendaConsultorioService service;

    @GET
    @Path("/unidade/{unidade}")
    public Response listarPorUnidade(@PathParam("unidade") String unidade, @QueryParam("ativos") String ativos) {
        boolean apenasAtivos = ativos == null || !"false".equalsIgnoreCase(ativos);
        return Response.ok(service.listarPorUnidade(unidade, apenasAtivos)).build();
    }

    @POST
    public Response salvar(ConfiguracaoAgendaConsultorioDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.status(Response.Status.CREATED).entity(service.salvar(dto)).build();
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") int id, ConfiguracaoAgendaConsultorioDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.ok(service.atualizar(id, dto)).build();
    }

    @PUT
    @Path("{id}/ativo")
    public Response alterarAtivo(@PathParam("id") int id, ConfiguracaoAgendaConsultorioDto dto) {
        return Response.ok(service.alterarAtivo(id, dto)).build();
    }

    @DELETE
    @Path("{id}")
    public Response excluir(@PathParam("id") int id) {
        service.excluir(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/excluir-lote")
    public Response excluirLote(List<Integer> ids) {
        long removidos = service.excluirVarios(ids);
        return Response.ok(Map.of("removidos", removidos)).build();
    }
}
