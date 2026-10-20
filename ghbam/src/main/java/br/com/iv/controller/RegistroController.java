package br.com.iv.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

	@PATCH
	@Path("/id/{id}/responsavel-observacao")
	public Response atualizarResponsavelObservacao(@PathParam("id") int id, Map<String, String> dados) {
		String responsavel = dados == null ? null : dados.get("responsavel");
		String observacao = dados == null ? null : dados.get("observacao");
		if (id <= 0 || responsavel == null || observacao == null || responsavel.length() > 10
				|| observacao.length() > 350) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(Map.of("erro", "Responsavel ou observacao ausente ou acima do limite permitido.")).build();
		}
		if (registroService.atualizarRegistroResponsavelObservacao(id, responsavel, observacao) == 0) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "Registro nao localizado.")).build();
		}
		return Response.noContent().build();
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
	public Response contarAtendimentosNaSemana(@PathParam("unidade") String unidade, @PathParam("servidor") String servidor,
			@PathParam("quando") String quando) {
		if (quando == null || !Set.of("dia", "semana", "mes", "ano").contains(quando)) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(Map.of("erro", "Informe dia, semana, mes ou ano.")).build();
		}
		return Response.ok(registroService.contarAtendimentosNaSemana(servidor, unidade, quando)).build();
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
