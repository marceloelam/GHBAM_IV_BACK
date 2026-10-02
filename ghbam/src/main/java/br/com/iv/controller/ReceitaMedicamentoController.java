package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.ReceitaMedicamentoDto;
import br.com.iv.entity.ReceitaMedicamentos;
import br.com.iv.service.ReceitaMedicamentoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("receitas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReceitaMedicamentoController {

	@Inject
	ReceitaMedicamentoService receitaMedicamentoService;


	@GET
	@Path("{bam}")
	public Response listarReceitaMedicamento(@PathParam("bam") int bam) {
		List<ReceitaMedicamentos> receitaMedicamentos = receitaMedicamentoService.listarReceitaMedica(bam);
		return Response.ok(receitaMedicamentos).build();
	}

	
	@GET
	@Path("/atualizarobs/{bam}/obs/{obs}") 
	public void atualizarRegistroEspera(@PathParam("bam") int bam, @PathParam("obs") String obs) {
		atualizarObservacao(bam, obs);
	}

	@PATCH
	@Path("/{bam}/observacao/{obs}") 
	public void atualizarObservacaoPatch(@PathParam("bam") int bam, @PathParam("obs") String obs) {
		atualizarObservacao(bam, obs);
	}

	private void atualizarObservacao(int bam, String obs) {
		receitaMedicamentoService.atualizarObservacaoReceita(bam, obs);
	}
		
	@POST
	public Response salvarReceitaMedicamento(ReceitaMedicamentoDto dto) {
		ReceitaMedicamentos receitaMedicamentos = receitaMedicamentoService.salvarReceitaMedicamento(dto);
		return Response.ok(receitaMedicamentos).status(201).build();
	}
	
	@DELETE
	@Path("/remove/{id}")
	public Response removerPaciente(@PathParam("id") int id, ReceitaMedicamentoDto dto) {
		receitaMedicamentoService.removeReceitaMedicamento(id);
		return Response.status(204).build();
	}

}
