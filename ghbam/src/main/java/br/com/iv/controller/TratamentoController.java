package br.com.iv.controller;

import java.util.List;

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

import br.com.iv.dto.TratamentoDto;
import br.com.iv.entity.Tratamento;
import br.com.iv.service.TratamentoService;

@Path("tratamento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TratamentoController {

	@Inject
	TratamentoService tratamentoService;

	@GET
	public Response listarTratamento() {
		List<Tratamento> tratamento = tratamentoService.listarTodosOsTratamentos();
		return Response.ok(tratamento).build();
	}

	@GET
	@Path("/id/{id}")
	public Tratamento listarPorID(@PathParam("id") int id) {
		return tratamentoService.listarPorID(id);
	}

	@GET
	@Path("/controle/{controle}")
	public Tratamento listarPorControle(@PathParam("controle") String controle) {
		return tratamentoService.listarPorControle(controle);
	}

	@GET
	@Path("/idtratamento/{idtratamento}")
	public void finalizarBam(@PathParam("idtratamento") int idtratamento) {
		finalizarBamPorPost(idtratamento);
	}

	@POST
	@Path("/{idtratamento}/finalizacao")
	public void finalizarBamPorPost(@PathParam("idtratamento") int idtratamento) {
		tratamentoService.finalizarBam(idtratamento);
	}
	
	@GET
	@Path("/idtratamentoidpaciente/{idtratamentoidpaciente}")
	public void finalizarBamIdPaciente(@PathParam("idtratamentoidpaciente") int idtratamentoidpaciente) {
		finalizarBamIdPacientePorPost(idtratamentoidpaciente);
	}

	@POST
	@Path("/paciente/{idtratamentoidpaciente}/finalizacao")
	public void finalizarBamIdPacientePorPost(@PathParam("idtratamentoidpaciente") int idtratamentoidpaciente) {
		tratamentoService.finalizarBamIdPaciente(idtratamentoidpaciente);
	}

	@GET
	@Path("/idtrat/{idtratamento}/alta/{alta}/tipo/{op}/usuario/{usuario}")
	public void finalizarAtendimentoMedico(@PathParam("idtratamento") int idtratamento,
			@PathParam("alta") String alta, @PathParam("op") String op, @PathParam("usuario") String usuario) {
		finalizarAtendimentoMedicoPorPost(idtratamento, alta, op, usuario);
	}

	@POST
	@Path("/{idtratamento}/alta/{alta}/tipo/{op}/usuario/{usuario}")
	public void finalizarAtendimentoMedicoPorPost(@PathParam("idtratamento") int idtratamento,
			@PathParam("alta") String alta, @PathParam("op") String op, @PathParam("usuario") String usuario) {
		tratamentoService.finalizarAtendimentoMedico(idtratamento, alta, op, usuario);
	}

	@POST
	public Response salvarTratamento(TratamentoDto dto) {
		Tratamento tratamento = tratamentoService.salvarTratamento(dto);
		return Response.ok(tratamento).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarTratamento(@PathParam("id") int id, TratamentoDto dto) {
		tratamentoService.atualizarTratamento(id, dto);
		return Response.status(204).build();
	}

}
