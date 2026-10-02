package br.com.iv.controller;

import java.util.List;

import br.com.iv.entity.ResumoFinalAtendimentoMedico;
import br.com.iv.service.ResumoFinalAvaliacaoMedicoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("resumomedico")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ResumoFinalAtendimentoMedicoController {

	@Inject
	ResumoFinalAvaliacaoMedicoService resumoFinalAvaliacaoMedicoService;

	@GET
	@Path("/data/{data}/unidade/{unidade}")
	public Response xlistarResumoMedico(@PathParam("data") String data, @PathParam("unidade") String unidade) {
		List<ResumoFinalAtendimentoMedico> resumo = resumoFinalAvaliacaoMedicoService.filtarPorResumoFinal(data, unidade);
		return Response.ok(resumo).build();
	}
	
	
	@GET
	@Path("/cancelar/{bam}")
	public Response cancelarResumoDeAlta(@PathParam("bam") String bam) {
		return cancelarResumoDeAltaPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/cancelamento")
	public Response cancelarResumoDeAltaPorPatch(@PathParam("bam") String bam) {
		return cancelarResumoDeAltaPorBam(bam);
	}

	private Response cancelarResumoDeAltaPorBam(String bam) {
		return resumoFinalAvaliacaoMedicoService.restaurarinternacao(bam);
	}
	
	
	

}
