package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.RegistroDto;
import br.com.iv.entity.Registro;
import br.com.iv.service.RegistroService;

@Path("registro")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RegistroController {

	@Inject
	RegistroService registroService;

	@GET
	public Response listarRegistro() {
		List<Registro> registro = registroService.listarTodosOsRegistros();
		return Response.ok(registro).build();
	}

	@GET
	@Path("/id/{id}")
	public Response listarPorID(@PathParam("id") int id) {
		Registro registro = registroService.listarPorID(id);
		if (registro == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "Registro nao localizado", "id", id))
					.build();
		}
		return Response.ok(registro).build();
	}

	@GET
	@Path("/controle/{controle}")
	public Response listarPorControle(@PathParam("controle") String controle) {
		Registro registro = registroService.listarPorControle(controle);
		if (registro == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "Registro nao localizado", "controle", controle))
					.build();
		}
		return Response.ok(registro).build();
	}

	@GET
	@Path("/idregistro/{idregistro}/novosetor/{novoSetor}")
	public void atualizarNovoSetorDeAtendimento(@PathParam("idregistro") int idregistro,
			@PathParam("novoSetor") String novoSetor) {
		atualizarNovoSetorDeAtendimentoPorPatch(idregistro, novoSetor);
	}

	@PATCH
	@Path("/{idregistro}/setor/{novoSetor}")
	public void atualizarNovoSetorDeAtendimentoPorPatch(@PathParam("idregistro") int idregistro,
			@PathParam("novoSetor") String novoSetor) {
		registroService.atualizarNovoSetorDeAtendimento(idregistro, novoSetor);
	}

	@GET
	@Path("/idreg/{idregistro}/responsavel/{responsavel}/obs/{obs}")
	public void atualizarRegistroResponsavelObservacao(@PathParam("idregistro") int idregistro,
			@PathParam("responsavel") String responsavel, @PathParam("obs") String obs) {
		registroService.atualizarRegistroResponsavelObservacao(idregistro, responsavel, obs);
	}
	
		
	@GET
	@Path("/informarexame/{bam}/{local}")
	public void atualizarRegistrinformarexame(@PathParam("bam") int bam,
			@PathParam("local") String local) {
		registroService.atualizarRegistroinformarexame(bam, local);
	}
	
	
	
	@GET
	@Path("/atualizarespera/{idregistro}/atendidopor/{atendidopor}")
	public void atualizarRegistroEspera(@PathParam("idregistro") int idregistro,
			@PathParam("atendidopor") String atendidopor) {
		atualizarRegistroEsperaPorPatch(idregistro, atendidopor);
	}

	@PATCH
	@Path("/{idregistro}/espera/atendido-por/{atendidopor}")
	public void atualizarRegistroEsperaPorPatch(@PathParam("idregistro") int idregistro,
			@PathParam("atendidopor") String atendidopor) {
		registroService.atualizarRegistroEspera(idregistro, atendidopor);
	}

	@GET
	@Path("/unidade/{unidade}/medico/{medico}")
	public Response locaisDeTriagem(@PathParam("unidade") String unidade, @PathParam("medico") String medico) {
		List<Registro> registros = registroService.duploAtendimentoMedico(unidade, medico);
		return Response.ok(registros).build();
	}

	@GET
	@Path("/ativossemana/{servidor}/unidade/{unidade}/quando/{quando}")
	public long contarAtendimentosNaSemana(@PathParam("unidade") String unidade, @PathParam("servidor") String servidor,
			@PathParam("quando") String quando) {
		return registroService.contarAtendimentosNaSemana(servidor, unidade, quando);
	}

	@POST
	public Response salvarRegistro(RegistroDto dto) {
		Registro registro = registroService.salvarRegistro(dto);
		return Response.ok(registro).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarRegistro(@PathParam("id") int id, RegistroDto dto) {
		registroService.atualizarRegistro(id, dto);
		return Response.status(204).build();
	}
	
	@GET
	@Path("/liberarpaciente/{id}")
	public void liberarPaciente(@PathParam("id") int id	) {
		liberarPacientePorRegistro(id);
	}

	@POST
	@Path("/{id}/liberacao")
	public void liberarPacientePorPost(@PathParam("id") int id) {
		liberarPacientePorRegistro(id);
	}

	private void liberarPacientePorRegistro(int id) {
		registroService.liberarPaciente(id);
	}
}
