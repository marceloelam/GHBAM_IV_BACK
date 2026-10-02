package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.EvolucaoPacienteDto;
import br.com.iv.entity.EvolucaoPaciente;
import br.com.iv.service.EvolucaoPacienteService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("evolucaopaciente")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvolucaoPacienteController {

	@Inject
	EvolucaoPacienteService evolucaoPacienteService;


	@GET
	@Path("/id/{id}")
	public Response EvolucaoPacienteID(@PathParam("id") int id) {
		EvolucaoPaciente evolucaoPacienteUnidade = evolucaoPacienteService.filtarPorId(id);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
	
	@GET
	@Path("/bam/{bam}/inicio/{inicio}/fim/{fim}")
	public Response listarEvolucaoPaciente(@PathParam("bam") int bam, @PathParam("inicio") String inicio, @PathParam("fim") String fim) {
		List<EvolucaoPaciente> evolucaoPacienteUnidade = evolucaoPacienteService.filtarPorBam(bam, inicio, fim);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
	
	// Bare /bam/{bam} (sem inicio/fim): varios editores no frontend chamam assim
	// esperando TODAS as evolucoes do bam, sem filtro de data. Mesmo resultado
	// que /bam2/{bam}, so que no path que o frontend ja usa.
	@GET
	@Path("/bam/{bam}")
	public Response listarEvolucaoPacienteBamSimples(@PathParam("bam") int bam) {
		List<EvolucaoPaciente> evolucaoPacienteUnidade = evolucaoPacienteService.filtarPorBam2(bam);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
		@GET
	@Path("/bam2/{bam}")
	public Response listarEvolucaoPaciente2(@PathParam("bam") int bam) {
		List<EvolucaoPaciente> evolucaoPacienteUnidade2 = evolucaoPacienteService.filtarPorBam2(bam);
		return Response.ok(evolucaoPacienteUnidade2).build();
	}
	
	@GET
	@Path("/evolucaobam2/{bam}")
	public Response filtarUltimaEvolucao(@PathParam("bam") int bam) {
		List<EvolucaoPaciente> filtarUltimaEvolucao = evolucaoPacienteService.filtarUltimaEvolucao(bam);
		return Response.ok(filtarUltimaEvolucao).build();
	}

	@GET
	@Path("/inativar/{id}")
	public Response inativar(@PathParam("id") int id) {
		return inativarEvolucaoPaciente(id);
	}

	@PATCH
	@Path("/{id}/inativacao")
	public Response inativarPorPatch(@PathParam("id") int id) {
		return inativarEvolucaoPaciente(id);
	}

	private Response inativarEvolucaoPaciente(int id) {
		try {
			evolucaoPacienteService.inativar(id);
			return Response.noContent().build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
		}
	}
	
	@GET
	@Path("/bamultimo/{bam}")
	public Response listarUltimaEvolucaoPaciente(@PathParam("bam") int bam) {
		EvolucaoPaciente evolucaoPacienteUnidade = evolucaoPacienteService.filtarUltimaPorBam(bam);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
	
	@POST
	public Response salvarEvolucaoPaciente(EvolucaoPacienteDto dto) {
		EvolucaoPaciente evolucaoPaciente = evolucaoPacienteService.salvarEvolucao(dto);
		return Response.ok(evolucaoPaciente).status(201).build();
	}

	

}
