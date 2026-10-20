package br.com.iv.controller;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.service.PainelVozService;
import br.com.iv.service.PainelVozService.VozIndisponivelException;

/**
 * Voz do painel de chamada. Só "status" e "chamada" são públicos (kiosk) — ver
 * RbacRequestFilter.isPublicKioskRequest. "vozes" e "exemplo" exigem kadm
 * (RoutePermissionCatalog) porque são usados na configuração da unidade.
 */
@Path("painelVoz")
public class PainelVozController {

	@Inject
	PainelVozService painelVozService;

	@GET
	@Path("/status/{sigla}")
	public Response status(@PathParam("sigla") String sigla) {
		return Response.ok(Map.of("ativo", painelVozService.ativoPara(sigla)))
				.type(MediaType.APPLICATION_JSON)
				.header("Cache-Control", "no-store")
				.build();
	}

	@GET
	@Path("/chamada/{sigla}/{tv}/{idchamada}")
	public Response audio(@PathParam("sigla") String sigla, @PathParam("tv") int tv,
			@PathParam("idchamada") int idchamada) {
		try {
			return painelVozService.audioDaChamada(sigla, tv, idchamada)
					.map(this::wav)
					.orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
		} catch (VozIndisponivelException e) {
			return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
		}
	}

	@GET
	@Path("/vozes")
	public Response vozes() {
		return Response.ok(painelVozService.listarVozes()).type(MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/exemplo/{voz}")
	public Response exemplo(@PathParam("voz") String voz, @QueryParam("frase") String frase) {
		try {
			return wav(painelVozService.exemplo(voz, frase));
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(Map.of("mensagem", e.getMessage()))
					.type(MediaType.APPLICATION_JSON)
					.build();
		} catch (VozIndisponivelException e) {
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
					.entity(Map.of("mensagem", e.getMessage()))
					.type(MediaType.APPLICATION_JSON)
					.build();
		}
	}

	private Response wav(byte[] audio) {
		return Response.ok(audio)
				.type("audio/wav")
				// Contém o nome do paciente: não deixar proxy/navegador guardar.
				.header("Cache-Control", "no-store")
				.build();
	}
}
