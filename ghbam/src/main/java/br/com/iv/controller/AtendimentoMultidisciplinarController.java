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

import br.com.iv.dto.AtendimentoMultidisciplinarDto;
import br.com.iv.dto.AtendimentoMultidisciplinarFilaDto;
import br.com.iv.entity.AtendimentoMultidisciplinar;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.AtendimentoMultidisciplinarService;

@Path("atendimentomultidisciplinar")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AtendimentoMultidisciplinarController {

	// Mantido em sincronia manual com a entrada "atendimentomultidisciplinar/*"
	// de RoutePermissionCatalog.java - esta checagem interna e' redundante com o
	// RbacRequestFilter, mas mais restrita por nao ter as permissoes granulares
	// (p*) abaixo, o que gerava 403 mesmo com o catalogo ja liberando o acesso.
	private static final List<String> PERMISSOES = List.of("kfisioterapia", "kassistencia", "knutricao",
			"kfonoaudiologia", "kpsicologia", "kfarmaciacentral", "kenfermagem",
			"pevolucaomultidisciplinar", "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto",
			"penfermariaseleitos", "pnir", "pccih", "kadm");

	@Inject
	AtendimentoMultidisciplinarService service;

	@Context
	ContainerRequestContext requestContext;

	@GET
	@Path("/fila")
	public List<AtendimentoMultidisciplinarFilaDto> listarFila(
			@QueryParam("atenderHoje") String atenderHoje,
			@QueryParam("unidade") String unidade,
			@QueryParam("setorPadrao") String setorPadrao) {
		exigirAcesso();
		return service.filaMultidisciplinar(atenderHoje, unidade, setorPadrao);
	}

	@GET
	@Path("/{id}")
	public AtendimentoMultidisciplinar buscarPorId(@PathParam("id") int id) {
		exigirAcesso();
		return service.buscarPorId(id);
	}

	@GET
	@Path("/paciente/{idpaciente}")
	public List<AtendimentoMultidisciplinar> listarPorPaciente(@PathParam("idpaciente") int idpaciente) {
		exigirAcesso();
		return service.listarPorPaciente(idpaciente);
	}

	@GET
	@Path("/bam/{idbam}")
	public List<AtendimentoMultidisciplinar> listarPorIdbam(@PathParam("idbam") int idbam) {
		exigirAcesso();
		return service.listarPorIdbam(idbam);
	}

	@POST
	public Response salvar(AtendimentoMultidisciplinarDto dto) {
		try {
			AtendimentoMultidisciplinar atendimento = service.salvar(dto, exigirAcesso());
			return Response.ok(atendimento).status(201).build();
		} catch (IllegalArgumentException e) {
			return erro(e.getMessage());
		}
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizar(@PathParam("id") int id, AtendimentoMultidisciplinarDto dto) {
		try {
			service.atualizar(id, dto, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return erro(e.getMessage());
		}
	}

	@POST
	@Path("/{id}/iniciar")
	public Response iniciar(@PathParam("id") int id) {
		try {
			service.iniciar(id, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return erro(e.getMessage());
		}
	}

	@POST
	@Path("/{id}/finalizar")
	public Response finalizar(@PathParam("id") int id, AtendimentoMultidisciplinarDto dto) {
		try {
			service.finalizar(id, dto, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return erro(e.getMessage());
		} catch (IllegalStateException e) {
			// assinaturaClinicaService.assinar() valida escopo/estado da assinatura
			// (unidade do usuario, unidade do documento, documento ja assinado) usando
			// IllegalStateException — sem este catch, essas validacoes legitimas
			// vazavam como 500 (mesmo padrao ja usado em OdontologiaController).
			return erro(Response.Status.CONFLICT, e.getMessage());
		}
	}

	@POST
	@Path("/{id}/cancelar")
	public Response cancelar(@PathParam("id") int id, String corpo) {
		try {
			String motivo = extrairMotivo(corpo);
			service.cancelar(id, motivo, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return erro(e.getMessage());
		}
	}

	@POST
	@Path("/{id}/reabrir")
	public Response reabrir(@PathParam("id") int id) {
		try {
			service.reabrir(id, exigirAcesso());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return erro(e.getMessage());
		}
	}

	private String extrairMotivo(String corpoJson) {
		if (corpoJson == null) {
			return "";
		}
		int inicio = corpoJson.indexOf(':');
		if (inicio < 0) {
			return corpoJson;
		}
		return corpoJson.substring(inicio + 1).replace("\"", "").replace("}", "").trim();
	}

	private Response erro(String mensagem) {
		return erro(Response.Status.BAD_REQUEST, mensagem);
	}

	private Response erro(Response.Status status, String mensagem) {
		return Response.status(status)
				.entity("{\"erro\":\"" + json(mensagem) + "\"}")
				.build();
	}

	private AuthenticatedUser exigirAcesso() {
		Object usuario = requestContext.getProperty("ghbam.user");
		if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
			throw new NotAuthorizedException("Bearer");
		}
		if (!autenticado.hasAnyPermission(PERMISSOES)) {
			throw new jakarta.ws.rs.ForbiddenException("Permissao insuficiente para acessar o Atendimento Multidisciplinar.");
		}
		return autenticado;
	}

	private String json(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
