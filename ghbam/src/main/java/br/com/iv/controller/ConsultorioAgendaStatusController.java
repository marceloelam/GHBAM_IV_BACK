package br.com.iv.controller;

import java.util.Optional;

import br.com.iv.dto.ConsultorioAgendaStatusDto;
import br.com.iv.entity.ConsultorioAgendaStatus;
import br.com.iv.service.ConsultorioAgendaStatusService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("consultorioagenda")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConsultorioAgendaStatusController {

    @Inject
    ConsultorioAgendaStatusService service;

    @GET
    @Path("/data/{data}/unidade/{unidade}")
    public Response listarPorDataUnidade(@PathParam("data") String data, @PathParam("unidade") String unidade) {
        return Response.ok(service.listarPorDataUnidade(data, unidade)).build();
    }

    @GET
    @Path("/agendamento/{idescalaPaciente}/unidade/{unidade}")
    public Response buscarPorAgendamento(@PathParam("idescalaPaciente") int idescalaPaciente,
            @PathParam("unidade") String unidade) {
        Optional<ConsultorioAgendaStatus> status = service.buscarPorAgendamento(idescalaPaciente, unidade);
        if (status.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(status.get()).build();
    }

    @POST
    @Path("/status")
    public Response salvarStatus(ConsultorioAgendaStatusDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        try {
            return Response.ok(service.salvarStatus(dto)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
