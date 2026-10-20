package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.InternacaoDto;
import br.com.iv.entity.Internacao;
import br.com.iv.service.BamRecenteGuard;
import br.com.iv.service.InternacaoService;
import br.com.iv.service.MigracaoBamInternacaoService;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

@Path("internacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InternacaoController {

	@Inject
	InternacaoService internacaoService;

	@Inject
	BamRecenteGuard bamRecenteGuard;

	@Inject
	MigracaoBamInternacaoService migracaoBamInternacaoService;

	// Leva a internação aberta e o leito de um BAM antigo para o último BAM do paciente.
	@POST
	@Path("/migrar-bam/{origem}/para/{destino}")
	public Response migrarBam(@PathParam("origem") int origem, @PathParam("destino") int destino,
			@Context SecurityContext seguranca) {
		String quem = seguranca != null && seguranca.getUserPrincipal() != null
				? seguranca.getUserPrincipal().getName() : "desconhecido";
		return Response.ok(migracaoBamInternacaoService.migrar(origem, destino, quem)).build();
	}

	@GET
	public Response listarInternacao() {
		List<Internacao> cbo = internacaoService.listarTodasInternacao();
		return Response.ok(cbo).build();
	}

	@GET
	@Path("/id/{id}")
	public Internacao listarPorIdUnico(@PathParam("id") String id) {
		return internacaoService.filtarPorUnico(id);
	}

	@POST
	public Response salvarInternacao(InternacaoDto dto, @QueryParam("confirmarBamAntigo") boolean confirmarBamAntigo) {
		// Fora do try: o catch abaixo engoliria o 409.
		bamRecenteGuard.recusarSeJaInternado(dto == null ? null : dto.getBam());
		bamRecenteGuard.exigirConfirmacao(dto == null ? null : dto.getBam(), confirmarBamAntigo, "Internacao");
		try {
			Internacao internacao = internacaoService.salvarInternacao(dto);
			return Response.ok(internacao).status(201).build();
		} catch (Exception e) {
			// TODO: handle exception
		}
		return null;
	}

	@SuppressWarnings("unused")
	@POST
	@Path("/alterar/{id}")
	public void alterarInternacao(@PathParam("id") int id, InternacaoDto dto) {
		Internacao internacao = internacaoService.alterarInternacao(id, dto); 
	}
	
	@GET
	@Path("/restaurarinternacao/{bam}")
	public Internacao restaurarinternacao(@PathParam("bam") String bam) {
		return internacaoService.restaurarinternacao(bam);
	}

	@PATCH
	@Path("/{bam}/restauracao")
	public Internacao restaurarInternacao(@PathParam("bam") String bam) {
		return internacaoService.restaurarinternacao(bam);
	}
	

}
