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

import br.com.iv.dto.UnidadeVideoDto;
import br.com.iv.entity.UnidadeVideo;
import br.com.iv.service.UnidadeVideoService;

@Path("unidadeVideos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UnidadeVideoController {

	@Inject
	UnidadeVideoService unidadeVideoService;

	@GET
	@Path("/unidade/{sigla}")
	public Response listarPorSigla(@PathParam("sigla") String sigla) {
		List<UnidadeVideo> videos = unidadeVideoService.listarPorSigla(sigla);
		return Response.ok(videos).build();
	}

	@GET
	@Path("/unidade/{sigla}/ativos")
	public Response listarAtivosPorSigla(@PathParam("sigla") String sigla) {
		List<UnidadeVideo> videos = unidadeVideoService.listarAtivosPorSigla(sigla);
		return Response.ok(videos).build();
	}

	@POST
	public Response salvar(UnidadeVideoDto dto) {
		UnidadeVideo unidadeVideo = unidadeVideoService.salvar(dto);
		return Response.ok(unidadeVideo).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizar(@PathParam("id") int id, UnidadeVideoDto dto) {
		unidadeVideoService.atualizar(id, dto);
		return Response.status(204).build();
	}

	@PUT
	@Path("/ordenar/{sigla}")
	public Response reordenar(@PathParam("sigla") String sigla, List<Integer> idsEmOrdem) {
		unidadeVideoService.reordenar(sigla, idsEmOrdem);
		return Response.status(204).build();
	}

	@PUT
	@Path("/renomearSigla/{siglaAntiga}/{siglaNova}")
	public Response renomearSigla(@PathParam("siglaAntiga") String siglaAntiga, @PathParam("siglaNova") String siglaNova) {
		unidadeVideoService.renomearSigla(siglaAntiga, siglaNova);
		return Response.status(204).build();
	}

	@DELETE
	@Path("/remover/{id}")
	public Response remover(@PathParam("id") int id) {
		unidadeVideoService.remover(id);
		return Response.status(204).build();
	}

	@GET
	@Path("/verificar/{id}")
	public Response verificar(@PathParam("id") int id) {
		UnidadeVideo unidadeVideo = unidadeVideoService.verificar(id);
		return Response.ok(unidadeVideo).build();
	}
}
