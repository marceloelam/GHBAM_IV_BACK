package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.EvolucaoPacienteDto;
import br.com.iv.dto.EvolucaoPacienteReceituarioDto;
import br.com.iv.entity.EvolucaoPaciente;
import br.com.iv.entity.EvolucaoPacienteReceituario;
import br.com.iv.service.EvolucaoPacienteReceituarioService;
import br.com.iv.service.EvolucaoPacienteService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("evolucaopacientereceituario")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvolucaoPacienteReceituarioController {

	@Inject
	EvolucaoPacienteReceituarioService evolucaoPacienteService;


	@GET
	@Path("/id/{id}")
	public Response EvolucaoPacienteID(@PathParam("id") int id) {
		EvolucaoPacienteReceituario evolucaoPacienteUnidade = evolucaoPacienteService.filtarPorId(id);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
	
	@GET
	@Path("/bam/{bam}")
	public Response listarEvolucaoPaciente(@PathParam("bam") int bam) {
		List<EvolucaoPacienteReceituario> evolucaoPacienteUnidade = evolucaoPacienteService.filtarPorBam(bam);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
	
	@GET
	@Path("/bamultimo/{bam}")
	public Response listarUltimaEvolucaoPaciente(@PathParam("bam") int bam) {
		EvolucaoPacienteReceituario evolucaoPacienteUnidade = evolucaoPacienteService.filtarUltimaPorBam(bam);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
	
	@POST
	public Response salvarEvolucaoPaciente(EvolucaoPacienteReceituarioDto dto) {
		EvolucaoPacienteReceituario evolucaoPaciente = evolucaoPacienteService.salvarEvolucao(dto);
		return Response.ok(evolucaoPaciente).status(201).build();
	}

	

}
