package br.com.iv.controller;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import br.com.iv.service.VideoProxyService;

@Path("videoProxy")
public class VideoProxyController {

	@Inject
	VideoProxyService videoProxyService;

	@Context
	UriInfo uriInfo;

	@GET
	@Path("/manifest")
	public Response manifest(@QueryParam("url") String url) {
		String baseProxy = uriInfo.getBaseUri().toString();
		String conteudo = videoProxyService.buscarEReescreverManifesto(url, baseProxy);
		return Response.ok(conteudo)
				.header("Content-Type", "application/vnd.apple.mpegurl")
				.header("Cache-Control", "no-cache, no-store")
				.build();
	}

	@GET
	@Path("/texto")
	public Response texto(@QueryParam("url") String url) {
		String conteudo = videoProxyService.buscarTextoBruto(url);
		return Response.ok(conteudo)
				.header("Content-Type", "text/plain; charset=utf-8")
				.header("Cache-Control", "no-cache, no-store")
				.build();
	}

	@GET
	@Path("/segmento")
	public Response segmento(@QueryParam("url") String url) {
		byte[] dados = videoProxyService.buscarSegmento(url);
		String contentType = url != null && url.toLowerCase().contains(".ts")
				? "video/mp2t"
				: "application/octet-stream";
		return Response.ok(dados)
				.header("Content-Type", contentType)
				.header("Cache-Control", "public, max-age=31536000, immutable")
				.build();
	}

	@GET
	@Path("/status")
	public Response status(@QueryParam("url") String url) {
		boolean online = videoProxyService.verificarDisponibilidade(url);
		return Response.ok(Map.of(
				"status", online ? "online" : "offline",
				"mensagem", online
						? "Canal acessível neste momento."
						: "O canal não respondeu dentro do tempo esperado."))
				.build();
	}
}
