package br.com.iv.controller;

import java.time.LocalDate;
import java.util.List;

import br.com.iv.dto.SaeEnfermagemDto;
import br.com.iv.service.SaeEnfermagemService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@Path("sae-enfermagem")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SaeEnfermagemController {

    @Inject
    SaeEnfermagemService service;

    @Context
    ContainerRequestContext requestContext;

    @GET
    public Response buscar(
            @QueryParam("bam") Integer bam,
            @QueryParam("idpaciente") Integer idpaciente,
            @QueryParam("data") String data,
            @QueryParam("tipo") String tipo) {
        LocalDate dataSae = parseData(data);
        if (dataSae == null || ((bam == null || bam <= 0) && (idpaciente == null || idpaciente <= 0))) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Informe bam ou idpaciente e uma data valida\"}")
                    .build();
        }

        exigirAcessoLeitura();
        SaeEnfermagemDto dto = service.buscar(bam, idpaciente, dataSae, tipo);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }

    @GET
    @Path("historico")
    public Response historico(
            @QueryParam("bam") Integer bam,
            @QueryParam("idpaciente") Integer idpaciente) {
        exigirAcessoLeitura();
        if ((bam == null || bam <= 0) && (idpaciente == null || idpaciente <= 0)) {
            return Response.ok(List.of()).build();
        }
        return Response.ok(service.listarHistorico(bam, idpaciente)).build();
    }

    @POST
    public Response salvar(SaeEnfermagemDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Dados da SAE obrigatorios\"}")
                    .build();
        }
        if (dto.getPaciente() == null || dto.getPaciente().isNull()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Paciente obrigatorio\"}")
                    .build();
        }

        try {
            SaeEnfermagemService.ResultadoSalvar resultado = service.salvar(dto, usuarioParaRegistro());
            return Response.status(resultado.criado() ? Response.Status.CREATED : Response.Status.OK)
                    .entity(resultado.sae())
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity("{\"erro\":\"" + json(e.getMessage()) + "\"}").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + json(e.getMessage()) + "\"}").build();
        }
    }

    @POST
    @Path("{uuid}/retificar")
    public Response retificar(@PathParam("uuid") String uuid, com.fasterxml.jackson.databind.JsonNode corpo) {
        try {
            String motivo = corpo == null ? "" : corpo.path("motivo").asText("");
            return Response.status(Response.Status.CREATED)
                    .entity(service.retificar(uuid, motivo, usuarioParaRegistro()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity("{\"erro\":\"" + json(e.getMessage()) + "\"}").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + json(e.getMessage()) + "\"}").build();
        }
    }

    private void exigirAcessoLeitura() {
        AuthenticatedUser usuario = usuarioAutenticado();
        if (!usuario.hasAnyPermission(List.of("kenfermagem", "psaeenfermagem", "kadm"))) {
            throw new jakarta.ws.rs.ForbiddenException("Permissão insuficiente para consultar SAE de Enfermagem.");
        }
    }

    private AuthenticatedUser usuarioParaRegistro() {
        AuthenticatedUser usuario = usuarioAutenticado();
        if (!usuario.hasAnyPermission(List.of("kenfermagem", "psaeenfermagem", "kadm"))) {
            throw new jakarta.ws.rs.ForbiddenException("Somente enfermeiro autorizado pode registrar ou concluir a SAE.");
        }
        return usuario;
    }

    private AuthenticatedUser usuarioAutenticado() {
        Object usuario = requestContext.getProperty("ghbam.user");
        if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
            throw new jakarta.ws.rs.NotAuthorizedException("Bearer");
        }
        return autenticado;
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private LocalDate parseData(String data) {
        try {
            return data == null || data.isBlank() ? null : LocalDate.parse(data.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
