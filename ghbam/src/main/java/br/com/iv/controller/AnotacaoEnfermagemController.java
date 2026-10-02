package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.AnotacaoEnfermagemDto;
import br.com.iv.entity.AnotacaoEnfermagem;
import br.com.iv.service.AnotacaoEnfermagemService;
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

@Path("anotacaoenfermagem")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AnotacaoEnfermagemController {

    @Inject
    AnotacaoEnfermagemService service;

    @GET
    @Path("/paciente/{idpaciente}")
    public Response listarPorPaciente(@PathParam("idpaciente") int idpaciente) {
        List<AnotacaoEnfermagem> itens = service.listarPorPaciente(idpaciente);
        return Response.ok(itens).build();
    }

    @GET
    @Path("/bam/{idbam}")
    public Response listarPorBam(@PathParam("idbam") int idbam) {
        List<AnotacaoEnfermagem> itens = service.listarPorBam(idbam);
        return Response.ok(itens).build();
    }

    @POST
    public Response salvar(AnotacaoEnfermagemDto dto) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Paciente obrigatorio\"}")
                    .build();
        }
        if (dto.getAnotacao() == null || dto.getAnotacao().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Anotacao obrigatoria\"}")
                    .build();
        }
        AnotacaoEnfermagem item = service.salvar(dto);
        return Response.status(Response.Status.CREATED).entity(item).build();
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") int id, AnotacaoEnfermagemDto dto) {
        AnotacaoEnfermagem item = service.atualizar(id, dto);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(item).build();
    }

    @PUT
    @Path("/{id}/inativar")
    public Response inativar(@PathParam("id") int id, AnotacaoEnfermagemDto dto) {
        boolean ok = service.inativar(id, dto == null ? new AnotacaoEnfermagemDto() : dto);
        return ok ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
