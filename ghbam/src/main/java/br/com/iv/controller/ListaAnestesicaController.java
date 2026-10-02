package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.ListaAnestesicaDto;
import br.com.iv.entity.ListaAnestesica;
import br.com.iv.service.ListaAnestesicaService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@Path("listaanestesica")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ListaAnestesicaController {

    @Inject
    ListaAnestesicaService listaAnestesicaService;

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/bam{bam}")
    public Response listarPorBam(@PathParam("bam") int bam) {
        usuarioMedico();
        List<ListaAnestesica> listas = listaAnestesicaService.listarPorBam(bam);
        return Response.ok(listas).build();
    }

    @GET
    @Path("/bam2/{bam}/data/{data}")
    public Response listarPorBamEData(@PathParam("bam") int bam, @PathParam("data") String data) {
        usuarioMedico();
        List<ListaAnestesica> listas = listaAnestesicaService.listarPorBamEData(bam, data);
        return Response.ok(listas).build();
    }

    @GET
    @Path("/id/{id}")
    public Response buscarPorId(@PathParam("id") int id) {
        usuarioMedico();
        return listaAnestesicaService.buscarPorId(id)
            .map(lista -> Response.ok(lista).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    public Response salvar(ListaAnestesicaDto dto) {
        ListaAnestesica listaAnestesica = listaAnestesicaService.salvar(dto, usuarioMedico());
        return Response.ok(listaAnestesica).status(201).build();
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") int id, ListaAnestesicaDto dto) {
        try {
            ListaAnestesica listaAnestesica = listaAnestesicaService.atualizar(id, dto, usuarioMedico());
            return Response.ok(listaAnestesica).build();
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (RuntimeException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("{id}")
    public Response remover(@PathParam("id") int id) {
        try {
            listaAnestesicaService.remover(id, usuarioMedico());
            return Response.status(204).build();
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (RuntimeException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    private AuthenticatedUser usuarioMedico() {
        Object usuario = requestContext.getProperty("ghbam.user");
        if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
            throw new jakarta.ws.rs.NotAuthorizedException("Bearer");
        }
        // Mantido em sincronia manual com a entrada "listaanestesica" de
        // RoutePermissionCatalog.java.
        if (!autenticado.hasAnyPermission(List.of("kmedico", "kenfermagem", "kfaturamento",
                "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                "pnir", "pccih", "kadm"))) {
            throw new jakarta.ws.rs.ForbiddenException("Permissão insuficiente para ficha anestésica.");
        }
        return autenticado;
    }
}
