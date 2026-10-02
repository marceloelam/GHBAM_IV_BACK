package br.com.iv.controller;

import java.util.List;
import java.util.Collections;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


import br.com.iv.dto.AtendMedicoDTO;
import br.com.iv.dto.AtendimentoMedicoDto;
import br.com.iv.entity.AtendimentoMedico;
import br.com.iv.service.AtendimentoMedicoService;

@Path("atendimentomedico")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AtendimentoMedicoController {

	@Inject
	AtendimentoMedicoService atendimentoMedicoService; 

	@GET
	public Response listarAtendimentoMedico() { 
		List<AtendimentoMedico> atendimentoMedico = atendimentoMedicoService.listarTodosOsAtendimentosMedicos();
		return Response.ok(atendimentoMedico).build(); 
	}

		
	@GET
	@Path("/id/{idPaciente}")
	public Response listarPorID(@PathParam("idPaciente") int idPaciente) {
		List<AtendimentoMedico> atendimentoMedico = atendimentoMedicoService.listarPorID(idPaciente);
		if (atendimentoMedico == null) {
			atendimentoMedico = Collections.emptyList();
		}
		return Response.ok(atendimentoMedico).build();
	}
	
	
	@GET
	@Path("/atenderhoje/{atenderHoje}/unidade/{unidade}/atendidopor/{atendidoPor}")
	public Response medicoAtenderHoje(@PathParam("atenderHoje") String atenderHoje, @PathParam("unidade") String unidade, @PathParam("atendidoPor") String atendidoPor) {
		List<AtendMedicoDTO> atendimento = atendimentoMedicoService.medicoAtenderHoje(atenderHoje, unidade, atendidoPor);
		return Response.ok(atendimento).build(); 
	}

	@GET
	@Path("/atenderhojeagenda/{atenderHoje}/unidade/{unidade}/atendidopor/{atendidoPor}")
	public Response medicoAtenderHojeAgenda(@PathParam("atenderHoje") String atenderHoje, @PathParam("unidade") String unidade, @PathParam("atendidoPor") String atendidoPor) {
		List<AtendMedicoDTO> atendimento = atendimentoMedicoService.medicoAtenderHojeAgenda(atenderHoje, unidade, atendidoPor);
		return Response.ok(atendimento).build(); 
	}

	
	@GET
	@Path("/atenderhoje/{atenderHoje}/unidade/{unidade}/setorpadrao/{setorpadrao}")
	public Response medicoAtenderHojeEspera(@PathParam("atenderHoje") String atenderHoje, @PathParam("unidade") String unidade, @PathParam("setorpadrao") String setorpadrao) {
		List<AtendMedicoDTO> atendimento = atendimentoMedicoService.medicoAtenderHojeEspera(atenderHoje, unidade, setorpadrao);
		return Response.ok(atendimento).build(); 
	}
	
	@GET
	@Path("/atenderhojeagenda/{atenderHoje}/unidade/{unidade}/setorpadrao/{setorpadrao}")
	public Response medicoAtenderHojeEsperaAgenda(@PathParam("atenderHoje") String atenderHoje, @PathParam("unidade") String unidade, @PathParam("setorpadrao") String setorpadrao) {
		List<AtendMedicoDTO> atendimento = atendimentoMedicoService.medicoAtenderHojeEsperaAgenda(atenderHoje, unidade, setorpadrao);
		return Response.ok(atendimento).build(); 
	}
	
	
	@POST
	public Response salvarAtendimentoMedico(AtendimentoMedicoDto dto) {
		AtendimentoMedico atendimentoMedico = atendimentoMedicoService.salvarAtendimentoMedico(dto);
		return Response.ok(atendimentoMedico).status(201).build();
	}

}
