package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PrescricaoAprazamentoChecagemDto;
import br.com.iv.entity.PrescricaoAprazamentoChecagem;
import br.com.iv.service.PrescricaoAprazamentoChecagemService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("prescricao/aprazamento-checagem")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PrescricaoAprazamentoChecagemController {

    @Inject
    PrescricaoAprazamentoChecagemService service;

    @GET
    @Path("/{idprescricao}")
    public Response listar(@PathParam("idprescricao") int idprescricao) {
        List<PrescricaoAprazamentoChecagem> horarios = service.listarPorPrescricao(idprescricao);
        return Response.ok(horarios).build();
    }

    @POST
    @Path("/{idprescricao}")
    public Response salvarHorarios(
            @PathParam("idprescricao") int idprescricao,
            List<PrescricaoAprazamentoChecagemDto> horarios) {
        try {
            return Response.ok(service.salvarHorarios(idprescricao, horarios == null ? List.of() : horarios)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{idprescricao}/horario/{indiceHorario}/checagem")
    public Response checar(
            @PathParam("idprescricao") int idprescricao,
            @PathParam("indiceHorario") int indiceHorario,
            PrescricaoAprazamentoChecagemDto dto) {
        try {
            return Response.ok(service.checarHorario(idprescricao, indiceHorario, dto)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{idprescricao}/horario/{indiceHorario}/checagem")
    public Response desfazerChecagem(
            @PathParam("idprescricao") int idprescricao,
            @PathParam("indiceHorario") int indiceHorario) {
        try {
            return Response.ok(service.desfazerChecagem(idprescricao, indiceHorario)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }
}
