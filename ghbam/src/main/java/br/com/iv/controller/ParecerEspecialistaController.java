package br.com.iv.controller;

import br.com.iv.dto.AnamneseInternacaoDto;
import br.com.iv.dto.ParecerEspecialistaDto;
import br.com.iv.entity.ParecerEspecialista;
import br.com.iv.service.ParecerEspecialistaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/parecerespecialista")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParecerEspecialistaController {

    @Inject
    ParecerEspecialistaService service;

    @GET
    public List<ParecerEspecialistaDto> getAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") int id) {
        ParecerEspecialistaDto dto = service.findByIdDto(id);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }
    
    @GET
    @Path("/solicitante/{bam}/unidade/{unidade}")
    public Response getByBam(@PathParam("bam") int bam, @PathParam("unidade") String unidade) {
        ParecerEspecialistaDto dto = service.findByBamDto(bam, unidade);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }
    
    @GET
    @Path("/especialidadedestino/{especialidade}/unidade/{unidade}")
    public Response getByEspecialidade(@PathParam("especialidade") String especialidade, @PathParam("unidade") String unidade) {
        List<ParecerEspecialista>  dto = service.listaDePareceres(especialidade, unidade);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }
    

    @POST
    public Response create(ParecerEspecialistaDto dto) {
        ParecerEspecialistaDto created = service.create(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/atualizar/{id}")
    public Response update(@PathParam("id") int id, ParecerEspecialistaDto dto) {
        ParecerEspecialistaDto updated = service.update(id, dto);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @PUT
    @Path("/responder/{id}")
    public Response responder(@PathParam("id") int id, ParecerEspecialistaDto dto) {
        ParecerEspecialistaDto updated = service.responder(id, dto);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") int id) {
        boolean deleted = service.delete(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/anamneseinternacao/{nbam}/unidade/{unidade}")
    public Response getAnamneseInternacao(@PathParam("nbam") int nbam, @PathParam("unidade") String unidade) {
        List<AnamneseInternacaoDto> result = service.findAnamneseInternacao(nbam, unidade);
        return Response.ok(result).build();
    }
}
