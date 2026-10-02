package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.MultidisciplinarEncaminhamentoDto;
import br.com.iv.entity.MultidisciplinarEncaminhamento;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.MultidisciplinarEncaminhamentoService;

@Path("multidisciplinarencaminhamento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MultidisciplinarEncaminhamentoController {

	private static final List<String> PERMISSOES = List.of("kfisioterapia", "kassistencia", "knutricao",
			"kfonoaudiologia", "kpsicologia", "kfarmaciacentral", "kenfermagem", "kadm");

	@Inject
	MultidisciplinarEncaminhamentoService service;

	@Context
	ContainerRequestContext requestContext;

	@GET
	@Path("/paciente/{idpaciente}")
	public List<MultidisciplinarEncaminhamento> listarPorPaciente(@PathParam("idpaciente") int idpaciente) {
		exigirAcesso();
		return service.listarPorPaciente(idpaciente);
	}

	@GET
	@Path("/destino/{categoria}")
	public List<MultidisciplinarEncaminhamento> listarPorCategoriaDestino(
			@PathParam("categoria") String categoria, @QueryParam("status") String status) {
		exigirAcesso();
		return service.listarPorCategoriaDestino(categoria, status);
	}

	@POST
	public Response salvar(MultidisciplinarEncaminhamentoDto dto) {
		try {
			MultidisciplinarEncaminhamento encaminhamento = service.salvar(dto, exigirAcesso());
			return Response.ok(encaminhamento).status(201).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + json(e.getMessage()) + "\"}").build();
		}
	}

	@PUT
	@Path("/{id}/status/{status}")
	public Response atualizarStatus(@PathParam("id") int id, @PathParam("status") String status) {
		exigirAcesso();
		service.atualizarStatus(id, status);
		return Response.status(204).build();
	}

	private AuthenticatedUser exigirAcesso() {
		Object usuario = requestContext.getProperty("ghbam.user");
		if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
			throw new NotAuthorizedException("Bearer");
		}
		if (!autenticado.hasAnyPermission(PERMISSOES)) {
			throw new jakarta.ws.rs.ForbiddenException("Permissao insuficiente para acessar encaminhamentos.");
		}
		return autenticado;
	}

	private String json(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
