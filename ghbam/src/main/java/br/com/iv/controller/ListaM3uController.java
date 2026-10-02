package br.com.iv.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import br.com.iv.dto.ListaM3uDto;
import br.com.iv.service.ListaM3uService;
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

@Path("listasM3u")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ListaM3uController {
	@Inject
	ListaM3uService listaM3uService;

	@GET
	public Response listar() {
		return Response.ok(listaM3uService.listar()).build();
	}

	@POST
	public Response criar(ListaM3uDto dto) {
		try {
			return Response.status(Response.Status.CREATED).entity(listaM3uService.criar(dto)).build();
		} catch (IllegalStateException e) {
			return conflito(e.getMessage());
		} catch (IllegalArgumentException e) {
			return invalida(e.getMessage());
		}
	}

	@PUT
	@Path("/{id}")
	public Response atualizar(@PathParam("id") Long id, ListaM3uDto dto) {
		try {
			return Response.ok(listaM3uService.atualizar(id, dto)).build();
		} catch (NoSuchElementException e) {
			return naoEncontrada(e.getMessage());
		} catch (IllegalStateException e) {
			return conflito(e.getMessage());
		} catch (IllegalArgumentException e) {
			return invalida(e.getMessage());
		}
	}

	@DELETE
	@Path("/{id}")
	public Response excluir(@PathParam("id") Long id) {
		try {
			listaM3uService.excluir(id);
			return Response.noContent().build();
		} catch (NoSuchElementException e) {
			return naoEncontrada(e.getMessage());
		} catch (IllegalArgumentException e) {
			return invalida(e.getMessage());
		}
	}

	private Response invalida(String mensagem) {
		return resposta(Response.Status.BAD_REQUEST, mensagem);
	}

	private Response naoEncontrada(String mensagem) {
		return resposta(Response.Status.NOT_FOUND, mensagem);
	}

	private Response conflito(String mensagem) {
		return resposta(Response.Status.CONFLICT, mensagem);
	}

	private Response resposta(Response.Status status, String mensagem) {
		return Response.status(status).entity(Map.of("mensagem", mensagem)).build();
	}
}
