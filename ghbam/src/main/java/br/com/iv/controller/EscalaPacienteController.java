package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.EscalaPacienteDto;
import br.com.iv.dto.EscalaResumoDTO;
import br.com.iv.dto.SalvarEscalaPaciente2Dto;
import br.com.iv.entity.EscalaPaciente;
import br.com.iv.service.EscalaPacientesService;
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

@Path("escalapaciente")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EscalaPacienteController {

	@Inject
	EscalaPacientesService escalaPacientesService;

	@GET
	@Path("/idp/{idp}/unidade/{unidade}/presenca/{presenca}")
	public void xlistarEscalaData(@PathParam("idp") int idp, @PathParam("unidade") String unidade,
			@PathParam("presenca") boolean presenca) {
		escalaPacientesService.escalaPacientePorId2(idp, unidade, presenca);
	}
	
	@GET
	@Path("/cpf/{cpf}")
	public Response xlistarEscalaCpf(@PathParam("cpf") String cpf) {
		List<EscalaPaciente> especialidadeocpf = escalaPacientesService.xlocalizacpf(cpf);
		return Response.ok(especialidadeocpf).build();
	}

	@GET
	@Path("/idpac/{idp}")
	public Response xlistarEscalaIdPaciente(@PathParam("idp") int idp) {
		EscalaPaciente especialidadeo = escalaPacientesService.escalaPacientePorId(idp);
		return Response.ok(especialidadeo).build();
	}
	
	@GET
	@Path("/cancelaragendamento/{idbam}")
	public void cancelaragendamentonodestino(@PathParam("idbam") int idbam) {
		cancelarAgendamentoPorBam(idbam);
	}

	@PATCH
	@Path("/{idbam}/cancelamento")
	public void cancelarAgendamento(@PathParam("idbam") int idbam) {
		cancelarAgendamentoPorBam(idbam);
	}

	private void cancelarAgendamentoPorBam(int idbam) {
		escalaPacientesService.cancelaragendamentonodestino(idbam);
	}


	@GET
	@Path("/data/{data}/unidade/{unidade}")
	public Response xlistarEscalaData(@PathParam("data") String data, @PathParam("unidade") String unidade) {
		List<EscalaPaciente> especialidadeo = escalaPacientesService.xlocalizaDataUnidade(data, unidade);
		return Response.ok(especialidadeo).build();
	}

	@GET
	@Path("/especialidade/{especialidade}/unidade/{unidade}")
	public Response xlistarEscalaPaciente(@PathParam("especialidade") String especialidade,
			@PathParam("unidade") String unidade) {
		List<EscalaPaciente> especialidadeo = escalaPacientesService.xlocalizaEspecialidade(especialidade, unidade);
		return Response.ok(especialidadeo).build();
	}

	@GET
	@Path("/especialidadehora/{especialidade}/unidade/{unidade}/data/{data}/medico/{medico}")
	public Response xlistarEscalaPacienteHora(@PathParam("especialidade") String especialidade,
			@PathParam("unidade") String unidade, @PathParam("data") String data, @PathParam("medico") String medico) {
		List<EscalaResumoDTO> especialidadeo = escalaPacientesService.xlocalizaEspecialidadeHora(especialidade, unidade,
				data, medico);
		return Response.ok(especialidadeo).build();
	}

	@POST
	public Response salvarPacientes(EscalaPacienteDto dto) {
		try {
			EscalaPaciente escalaPacientes = escalaPacientesService.salvarEscalaPaciente(dto);
			return Response.ok(escalaPacientes).status(Response.Status.CREATED).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(e.getMessage())).build();
		} catch (Exception e) {
			return Response.serverError().entity(new ErrorResponse("Erro ao salvar paciente")).build();
		}
	}

	@POST
	@Path("/agenda")
	public Response salvarPacientesAgenda(SalvarEscalaPaciente2Dto dto) {
		try {
			EscalaPaciente escalaPacientes = escalaPacientesService.salvarEscalaPaciente2(dto);
			return Response.ok(escalaPacientes).status(Response.Status.CREATED).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(e.getMessage())).build();
		} catch (Exception e) {
			return Response.serverError().entity(new ErrorResponse("Erro ao salvar paciente")).build();
		}
	}

	// Classe interna para respostas de erro
	public static class ErrorResponse {
		public String message;

		public ErrorResponse(String message) {
			this.message = message;
		}
	}

	@PATCH
	@Path("/checkin/{tempcod}")
	public Response checkin(@PathParam("tempcod") String tempcod) {
		boolean updated = escalaPacientesService.checkinByTempcod(tempcod);
		if (updated) {
			return Response.ok().build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@jakarta.ws.rs.DELETE
	@Path("/idpDeletar/{idp}")
	public Response xdeletarAgendamento(@PathParam("idp") int idp) {
		escalaPacientesService.deletarAgendamento(idp);
		return Response.ok().build();
	}
}
