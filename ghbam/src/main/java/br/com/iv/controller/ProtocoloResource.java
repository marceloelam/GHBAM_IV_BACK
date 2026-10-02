package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.entity.Protocolo;
import br.com.iv.service.ProtocoloService;

@Path("protocolos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProtocoloResource {

	@Inject
	ProtocoloService protocoloService;

	@GET
	public Response listarTodos() {
		List<Protocolo> lista = protocoloService.listarTodos();
		return Response.ok(lista).build();
	}

	@GET
	@Path("/{id}")
	public Response buscarPorId(@PathParam("id") int id) {
		Protocolo protocolo = protocoloService.buscarPorId(id);
		if (protocolo == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{\"error\": \"Protocolo nao encontrado\"}").build();
		}
		return Response.ok(protocolo).build();
	}

	@GET
	@Path("/especialidade/{especialidade}")
	public Response buscarPorEspecialidade(@PathParam("especialidade") String especialidade) {
		List<Protocolo> lista = protocoloService.buscarPorEspecialidade(especialidade);
		return Response.ok(lista).build();
	}

	@GET
	@Path("/nome/{nome}")
	public Response buscarPorNome(@PathParam("nome") String nome) {
		Protocolo protocolo = protocoloService.buscarPorNome(nome);
		if (protocolo == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{\"error\": \"Protocolo nao encontrado\"}").build();
		}
		return Response.ok(protocolo).build();
	}

	@POST
	public Response salvar(Protocolo protocolo) {
		if (protocolo == null || protocolo.getNome() == null || protocolo.getNome().trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{\"error\": \"Nome do protocolo e obrigatorio\"}").build();
		}
		Protocolo salvo = protocoloService.salvar(protocolo);
		return Response.status(Response.Status.CREATED).entity(salvo).build();
	}

	@PUT
	@Path("/{id}")
	public Response atualizar(@PathParam("id") int id, Protocolo dados) {
		Protocolo atualizado = protocoloService.atualizar(id, dados);
		if (atualizado == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{\"error\": \"Protocolo nao encontrado\"}").build();
		}
		return Response.ok(atualizado).build();
	}

	@DELETE
	@Path("/{id}")
	public Response deletar(@PathParam("id") int id) {
		boolean removido = protocoloService.deletar(id);
		if (!removido) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{\"error\": \"Protocolo nao encontrado\"}").build();
		}
		return Response.ok("{\"message\": \"Protocolo removido com sucesso\"}").build();
	}
}
