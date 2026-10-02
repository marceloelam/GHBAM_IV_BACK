package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PrescricaoItemCicloEventoDto;
import br.com.iv.entity.PrescricaoItemCicloEvento;
import br.com.iv.service.PrescricaoItemCicloEventoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("prescricao-itens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PrescricaoItemCicloEventoController {

    @Inject
    PrescricaoItemCicloEventoService service;

    @GET
    @Path("/bam/{idbam}/ciclo")
    public Response listarPorBam(@PathParam("idbam") int idbam) {
        List<PrescricaoItemCicloEvento> eventos = service.listarPorBam(idbam);
        return Response.ok(eventos).build();
    }

    @GET
    @Path("/{iditem}/ciclo")
    public Response listarPorItem(@PathParam("iditem") int iditem) {
        List<PrescricaoItemCicloEvento> eventos = service.listarPorItem(iditem);
        return Response.ok(eventos).build();
    }

    @POST
    @Path("/{iditem}/ciclo")
    public Response registrar(@PathParam("iditem") int iditem, PrescricaoItemCicloEventoDto dto) {
        if (dto == null || vazio(dto.getTipoEvento())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"tipoEvento e obrigatorio\"}")
                    .build();
        }

        PrescricaoItemCicloEvento evento = service.registrar(iditem, dto);
        if (evento == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.status(Response.Status.CREATED).entity(evento).build();
    }

    private boolean vazio(String value) {
        return value == null || value.trim().isEmpty();
    }
}
