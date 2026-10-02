package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.ConsultaPediatricaDto;
import br.com.iv.entity.ConsultaPediatrica;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ConsultaPediatricaService;

@Path("consultapediatrica")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConsultaPediatricaController {

	@Inject
	ConsultaPediatricaService consultaPediatricaService;

	@Context
	ContainerRequestContext requestContext;

	@GET
	@Path("/paciente/{idpaciente}")
	public List<ConsultaPediatrica> listarPorPaciente(@PathParam("idpaciente") int idpaciente) {
		exigirAcesso();
		return consultaPediatricaService.listarPorPaciente(idpaciente);
	}

	@GET
	@Path("/{id}")
	public ConsultaPediatrica buscarPorId(@PathParam("id") int id) {
		exigirAcesso();
		return consultaPediatricaService.buscarPorId(id);
	}

	@GET
	@Path("/prontuario/{prontuario}")
	public List<ConsultaPediatrica> listarPorProntuario(@PathParam("prontuario") String prontuario) {
		exigirAcesso();
		return consultaPediatricaService.listarPorProntuario(prontuario);
	}

	@GET
	@Path("/bam/{idbam}")
	public List<ConsultaPediatrica> listarPorIdbam(@PathParam("idbam") int idbam) {
		exigirAcesso();
		return consultaPediatricaService.listarPorIdbam(idbam);
	}

	@POST
	public Response salvar(ConsultaPediatricaDto dto) {
		ConsultaPediatrica consulta = consultaPediatricaService.salvar(dto, exigirAcesso());
		return Response.ok(consulta).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizar(@PathParam("id") int id, ConsultaPediatricaDto dto) {
		try {
			consultaPediatricaService.atualizar(id, dto, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
					.build();
		}
	}

	@DELETE
	@Path("/{id}")
	public Response excluir(@PathParam("id") int id) {
		try {
			consultaPediatricaService.excluir(id, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
					.build();
		}
	}

	private AuthenticatedUser exigirAcesso() {
		Object usuario = requestContext.getProperty("ghbam.user");
		if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
			throw new NotAuthorizedException("Bearer");
		}
		// Mantido em sincronia manual com a entrada "consultapediatrica" de
		// RoutePermissionCatalog.java - lista bem mais restrita que o catalogo,
		// que gerava 403 mesmo pra papeis ja liberados pelo RbacRequestFilter.
		if (!autenticado.hasAnyPermission(List.of("kmedico", "kenfermagem", "ktriagem", "krecepcao", "kfaturamento",
				"presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
				"pnir", "pccih", "kadm"))) {
			throw new jakarta.ws.rs.ForbiddenException("Permissao insuficiente para acessar Visao Pediatrica.");
		}
		return autenticado;
	}

	private String json(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
