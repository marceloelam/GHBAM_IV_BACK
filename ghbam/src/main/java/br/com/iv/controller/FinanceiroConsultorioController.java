package br.com.iv.controller;

import java.util.List;
import java.util.Optional;

import br.com.iv.dto.FinanceiroConsultorioDto;
import br.com.iv.entity.FinanceiroConsultorio;
import br.com.iv.service.FinanceiroConsultorioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("financeiroconsultorio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FinanceiroConsultorioController {

    @Inject
    FinanceiroConsultorioService service;

    @GET
    @Path("/data1/{dataInicio}/data2/{dataFim}/unidade/{unidade}")
    public Response listarPorPeriodoUnidade(@PathParam("dataInicio") String dataInicio,
            @PathParam("dataFim") String dataFim,
            @PathParam("unidade") String unidade) {
        List<FinanceiroConsultorio> financeiro = service.listarPorPeriodoUnidade(dataInicio, dataFim, unidade);
        return Response.ok(financeiro).build();
    }

    @GET
    @Path("/paciente/{idpaciente}/unidade/{unidade}")
    public Response listarPorPaciente(@PathParam("idpaciente") int idpaciente, @PathParam("unidade") String unidade) {
        return Response.ok(service.listarPorPaciente(idpaciente, unidade)).build();
    }

    @GET
    @Path("/agendamento/{idescalaPaciente}/unidade/{unidade}")
    public Response buscarPorAgendamento(@PathParam("idescalaPaciente") int idescalaPaciente,
            @PathParam("unidade") String unidade) {
        Optional<FinanceiroConsultorio> financeiro = service.buscarPorAgendamento(idescalaPaciente, unidade);
        if (financeiro.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(financeiro.get()).build();
    }

    @POST
    public Response salvar(FinanceiroConsultorioDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.status(Response.Status.CREATED).entity(service.salvar(dto)).build();
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") int id, FinanceiroConsultorioDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.ok(service.atualizar(id, dto)).build();
    }

    @PUT
    @Path("{id}/status")
    public Response atualizarStatus(@PathParam("id") int id, FinanceiroConsultorioDto dto) {
        return Response.ok(service.atualizarStatus(id, dto)).build();
    }

    @PUT
    @Path("repasse/recebido")
    public Response marcarRepasseRecebido(FinanceiroConsultorioDto dto) {
        return Response.ok(service.marcarRepasseRecebido(dto)).build();
    }

    @PUT
    @Path("{id}/cancelar")
    public Response cancelar(@PathParam("id") int id, FinanceiroConsultorioDto dto) {
        return Response.ok(service.cancelar(id, dto)).build();
    }
}
