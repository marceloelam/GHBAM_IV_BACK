package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PrescricaoItemEstruturadoDto;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.service.PrescricaoItemEstruturadoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("prescricao-itens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PrescricaoItemEstruturadoController {

    @Inject
    PrescricaoItemEstruturadoService service;

    @GET
    @Path("/bam/{idbam}")
    public Response listarPorBam(
            @PathParam("idbam") int idbam,
            @QueryParam("ativos") @DefaultValue("false") boolean apenasAtivos) {
        List<PrescricaoItemEstruturado> itens = service.listarPorBam(idbam, apenasAtivos);
        return Response.ok(itens).build();
    }

    @GET
    @Path("/prescricao/{idprescricao}")
    public Response listarPorPrescricao(@PathParam("idprescricao") int idprescricao) {
        List<PrescricaoItemEstruturado> itens = service.listarPorPrescricao(idprescricao);
        return Response.ok(itens).build();
    }

    @POST
    public Response salvar(PrescricaoItemEstruturadoDto dto) {
        if (dto == null || dto.getIdbam() <= 0 || vazio(dto.getMedicamento())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"BAM e medicamento sao obrigatorios\"}")
                    .build();
        }
        try {
            PrescricaoItemEstruturado item = service.salvar(dto);
            return Response.status(Response.Status.CREATED).entity(item).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") int id, PrescricaoItemEstruturadoDto dto) {
        try {
            PrescricaoItemEstruturado item = service.atualizar(id, dto);
            if (item == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(item).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @PUT
    @Path("/{id}/suspender")
    public Response suspender(@PathParam("id") int id, PrescricaoItemEstruturadoDto dto) {
        try {
            PrescricaoItemEstruturado item = service.suspender(id, dto == null ? new PrescricaoItemEstruturadoDto() : dto);
            if (item == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(item).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    private boolean vazio(String value) {
        return value == null || value.trim().isEmpty();
    }
}
