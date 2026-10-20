package br.com.iv.controller;

import java.util.Map;

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

import br.com.iv.dto.UnidadeVideoCanalDto;
import br.com.iv.dto.UnidadeVideoConfigDto;
import br.com.iv.dto.UnidadeVideoVolumeDto;
import br.com.iv.entity.UnidadeVideoConfig;
import br.com.iv.service.UnidadeVideoConfigService;

@Path("unidadeVideoConfig")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UnidadeVideoConfigController {

	@Inject
	UnidadeVideoConfigService unidadeVideoConfigService;

	@GET
	@Path("/{sigla}")
	public Response buscar(@PathParam("sigla") String sigla) {
		UnidadeVideoConfig config = unidadeVideoConfigService.buscar(sigla);
		return Response.ok(config).build();
	}

	@PUT
	@Path("/{sigla}")
	public Response salvar(@PathParam("sigla") String sigla, UnidadeVideoConfigDto dto) {
		try {
			UnidadeVideoConfig config = unidadeVideoConfigService.salvar(sigla, dto);
			return Response.ok(config).build();
		} catch (IllegalArgumentException e) {
			// Frase da chamada ou voz do painel inválida.
			return respostaInvalida(e.getMessage());
		}
	}

	@GET
	@Path("/{sigla}/volume/{tv}")
	public Response buscarVolume(@PathParam("sigla") String sigla, @PathParam("tv") int tv) {
		try {
			return Response.ok(unidadeVideoConfigService.buscarVolume(sigla, tv)).build();
		} catch (IllegalArgumentException e) {
			return respostaInvalida(e.getMessage());
		}
	}

	@PUT
	@Path("/{sigla}/volume/{tv}")
	public Response salvarVolume(
			@PathParam("sigla") String sigla,
			@PathParam("tv") int tv,
			UnidadeVideoVolumeDto dto) {
		try {
			return Response.ok(unidadeVideoConfigService.salvarVolume(sigla, tv, dto)).build();
		} catch (IllegalArgumentException e) {
			return respostaInvalida(e.getMessage());
		}
	}

	@GET
	@Path("/{sigla}/canais")
	public Response listarCanais(@PathParam("sigla") String sigla) {
		try {
			return Response.ok(unidadeVideoConfigService.listarCanais(sigla)).build();
		} catch (IllegalArgumentException e) {
			return respostaInvalida(e.getMessage());
		}
	}

	@POST
	@Path("/{sigla}/canais")
	public Response adicionarCanal(@PathParam("sigla") String sigla, UnidadeVideoCanalDto dto) {
		try {
			return Response.status(Response.Status.CREATED)
					.entity(unidadeVideoConfigService.adicionarCanal(sigla, dto))
					.build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT)
					.entity(Map.of("mensagem", e.getMessage()))
					.build();
		} catch (IllegalArgumentException e) {
			return respostaInvalida(e.getMessage());
		}
	}

	@DELETE
	@Path("/{sigla}/canais/{canalId}")
	public Response removerCanal(@PathParam("sigla") String sigla, @PathParam("canalId") Long canalId) {
		try {
			unidadeVideoConfigService.removerCanal(sigla, canalId);
			return Response.noContent().build();
		} catch (IllegalArgumentException e) {
			return respostaInvalida(e.getMessage());
		}
	}

	@PUT
	@Path("/{sigla}/volume/{tv}/canal/{canalId}")
	public Response selecionarCanal(
			@PathParam("sigla") String sigla,
			@PathParam("tv") int tv,
			@PathParam("canalId") Long canalId) {
		try {
			return Response.ok(unidadeVideoConfigService.selecionarCanal(sigla, tv, canalId)).build();
		} catch (IllegalArgumentException e) {
			return respostaInvalida(e.getMessage());
		}
	}

	private Response respostaInvalida(String mensagem) {
		return Response.status(Response.Status.BAD_REQUEST)
				.entity(Map.of("mensagem", mensagem))
				.build();
	}
}
