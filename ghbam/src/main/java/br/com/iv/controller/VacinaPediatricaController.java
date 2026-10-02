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

import br.com.iv.dto.VacinaPediatricaDto;
import br.com.iv.entity.VacinaPediatrica;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.VacinaPediatricaService;

@Path("vacinapediatrica")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VacinaPediatricaController {

	@Inject
	VacinaPediatricaService vacinaPediatricaService;

	@Context
	ContainerRequestContext requestContext;

	@GET
	@Path("/paciente/{idpaciente}")
	public List<VacinaPediatrica> listarPorPaciente(@PathParam("idpaciente") int idpaciente) {
		exigirAcesso();
		return vacinaPediatricaService.listarPorPaciente(idpaciente);
	}

	@POST
	public Response salvar(VacinaPediatricaDto dto) {
		VacinaPediatrica vacina = vacinaPediatricaService.salvar(dto, exigirAcesso());
		return Response.ok(vacina).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizar(@PathParam("id") int id, VacinaPediatricaDto dto) {
		try {
			vacinaPediatricaService.atualizar(id, dto, exigirAcesso());
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
			vacinaPediatricaService.excluir(id, exigirAcesso());
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
		// Mantido em sincronia manual com a entrada "vacinapediatrica" de
		// RoutePermissionCatalog.java.
		if (!autenticado.hasAnyPermission(List.of("kmedico", "kenfermagem", "ktriagem", "krecepcao", "kfaturamento",
				"presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
				"pnir", "pccih", "kadm"))) {
			throw new jakarta.ws.rs.ForbiddenException("Permissao insuficiente para acessar Calendario Vacinal.");
		}
		return autenticado;
	}

	private String json(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
