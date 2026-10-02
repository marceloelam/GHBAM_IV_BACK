package br.com.iv.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.LaudoAutorizacaoDto;
import br.com.iv.entity.LaudoAutorizacao;
import br.com.iv.service.LaudoAutorizacaoService;

@Path("laudoautorizacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LaudoAutorizacaoController {

	@Inject
	LaudoAutorizacaoService laudoAutorizacaoService;

	@GET
	@Path("/laudoBam/{bam}")
	public Response xlistarLaudoAutorizacao(@PathParam("bam") int ybam) {
		LaudoAutorizacao laudoautorizacao = laudoAutorizacaoService.filtarPorBam(ybam);
		return Response.ok(laudoautorizacao).build();
	}

	@POST
	public Response salvarlaudoautorizacao(LaudoAutorizacaoDto dto) {
		LaudoAutorizacao laudoautorizacao = laudoAutorizacaoService.salvarLaudoAutorizacao(dto);
		return Response.ok(laudoautorizacao).status(201).build();
	}

	@POST
	@Path("/{id}/encaminhar-nir")
	public Response encaminharAoNir(@PathParam("id") int id) {
		LaudoAutorizacao laudoautorizacao = laudoAutorizacaoService.encaminharAoNir(id);
		return Response.ok(laudoautorizacao).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarBam(@PathParam("id") int id, LaudoAutorizacaoDto dto) {
		laudoAutorizacaoService.atualizarPaciente(id, dto);
		return Response.status(204).build(); 
	}

}
