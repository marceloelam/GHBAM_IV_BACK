package br.com.iv.controller;

import java.io.IOException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import br.com.iv.entity.UnidadeVideo;
import br.com.iv.service.VideoArquivoService;

@Path("video")
public class VideoArquivoController {

	@Inject
	VideoArquivoService videoArquivoService;

	@Context
	UriInfo uriInfo;

	@GET
	@Path("/{sigla}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response listar(@PathParam("sigla") String sigla) {
		List<String> arquivos = videoArquivoService.listarArquivos(sigla);
		return Response.ok(arquivos).build();
	}

	@POST
	@Path("/{sigla}/sincronizar")
	@Produces(MediaType.APPLICATION_JSON)
	public Response sincronizar(@PathParam("sigla") String sigla) {
		List<UnidadeVideo> videos = videoArquivoService.sincronizar(sigla, uriInfo.getBaseUri().toString());
		return Response.ok(videos).build();
	}

	@GET
	@Path("/{sigla}/{arquivo}")
	public Response servir(@PathParam("sigla") String sigla, @PathParam("arquivo") String arquivo) {
		java.nio.file.Path caminho = videoArquivoService.resolverArquivo(sigla, arquivo);
		try {
			byte[] conteudo = java.nio.file.Files.readAllBytes(caminho);
			return Response.ok(conteudo)
					.header("Content-Type", videoArquivoService.tipoConteudo(arquivo))
					.header("Accept-Ranges", "bytes")
					.build();
		} catch (IOException e) {
			return Response.status(500).build();
		}
	}
}
