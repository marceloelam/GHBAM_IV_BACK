package br.com.iv.controller;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.entity.ProtocoloExecucao;
import br.com.iv.service.ProtocoloExecucaoService;

@Path("protocolos-execucao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProtocoloExecucaoResource {

    @Inject
    ProtocoloExecucaoService service;

    @GET
    public Response listarTodos() {
        List<ProtocoloExecucao> lista = service.listarTodos();
        return Response.ok(lista).build();
    }

    @GET
    @Path("/{id}")
    public Response buscarPorId(@PathParam("id") int id) {
        ProtocoloExecucao execucao = service.buscarPorId(id);
        if (execucao == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Execução não encontrada\"}").build();
        }
        return Response.ok(execucao).build();
    }

    @GET
    @Path("/unidade/{unidade}")
    public Response buscarPorUnidade(@PathParam("unidade") String unidade) {
        List<ProtocoloExecucao> lista = service.buscarPorUnidade(unidade);
        return Response.ok(lista).build();
    }

    @GET
    @Path("/protocolo/{protocoloId}")
    public Response buscarPorProtocoloId(@PathParam("protocoloId") int protocoloId) {
        List<ProtocoloExecucao> lista = service.buscarPorProtocoloId(protocoloId);
        return Response.ok(lista).build();
    }

    @GET
    @Path("/executor/{servidor}")
    public Response buscarPorExecutor(@PathParam("servidor") String servidor) {
        List<ProtocoloExecucao> lista = service.buscarPorServidorExecutor(servidor);
        return Response.ok(lista).build();
    }

    @POST
    public Response salvar(ProtocoloExecucao execucao) {
        if (execucao == null || execucao.getProtocoloId() == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Dados inválidos\"}").build();
        }
        ProtocoloExecucao salvo = service.salvar(execucao);
        return Response.status(Response.Status.CREATED).entity(salvo).build();
    }

    @PUT
    @Path("/{id}/resultados")
    public Response atualizarResultados(@PathParam("id") int id, String bodyJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(bodyJson);

            String resultadosJson = node.has("resultadosJson") ? node.get("resultadosJson").asText() : null;
            String acoesExecutadasJson = node.has("acoesExecutadasJson") ? node.get("acoesExecutadasJson").asText() : null;
            String alertasJson = node.has("alertasJson") ? node.get("alertasJson").asText() : null;
            String examesSolicitadosJson = node.has("examesSolicitadosJson") ? node.get("examesSolicitadosJson").asText() : null;
            String tarefasCriadasJson = node.has("tarefasCriadasJson") ? node.get("tarefasCriadasJson").asText() : null;

            ProtocoloExecucao atualizado = service.atualizarResultados(id,
                    resultadosJson, acoesExecutadasJson, alertasJson,
                    examesSolicitadosJson, tarefasCriadasJson);

            if (atualizado == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Execução não encontrada\"}").build();
            }
            return Response.ok(atualizado).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Erro ao processar: " + e.getMessage() + "\"}").build();
        }
    }

    @PUT
    @Path("/{id}/finalizar")
    public Response finalizar(@PathParam("id") int id, String bodyJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(bodyJson);

            String status = node.has("status") ? node.get("status").asText() : "concluido";
            String observacoes = node.has("observacoes") ? node.get("observacoes").asText() : null;

            ProtocoloExecucao atualizado = service.atualizarStatus(id, status, observacoes);
            if (atualizado == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Execução não encontrada\"}").build();
            }
            return Response.ok(atualizado).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Erro ao processar: " + e.getMessage() + "\"}").build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") int id) {
        boolean removido = service.deletar(id);
        if (!removido) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Execução não encontrada\"}").build();
        }
        return Response.ok("{\"message\": \"Execução removida com sucesso\"}").build();
    }
}
