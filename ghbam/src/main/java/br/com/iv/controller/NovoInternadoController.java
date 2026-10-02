package br.com.iv.controller;

import java.time.LocalDate;
import java.util.List;

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

import br.com.iv.dto.NovoInternadoDto;
import br.com.iv.entity.NovoInternado;
import br.com.iv.service.NovoInternadoService;

@Path("novointernado")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NovoInternadoController {

	@Inject
	NovoInternadoService novointernadoService;

	@GET
	public Response listarRegistro() {
		List<NovoInternado> novointernado = novointernadoService.listarTodosOsRegistros();
		return Response.ok(novointernado).build();
	}
	
	
	
	@GET
	@Path("/ativos/{unidade}")
	public Response listarRegistroAtivos(@PathParam("unidade") String unidade) {
		List<NovoInternado> novointernado = novointernadoService.listarTodosOsRegistrosAtivos(unidade);
		return Response.ok(novointernado).build();
	}

	@GET
	@Path("/bam/{bam}/unidade/{unidade}")
	public Response listarBamUnidade(@PathParam("bam") String bam, @PathParam("unidade") String unidade) {
		List<NovoInternado> novointernado = novointernadoService.listarBamUnidade(bam, unidade);
		return Response.ok(novointernado).build();
	}


	@GET
	@Path("/unidadenir/{unidade}/quem/{quem}") // Leitos ocupados ( PAINEL )
	public long contagem(@PathParam("unidade") String unidade, @PathParam("quem") String quem) {
		return novointernadoService.contagem(unidade, quem);
	}

	@GET
	@Path("/id/{id}")
	public Response listarPorIdUnico(@PathParam("id") int id) {
		NovoInternado result = novointernadoService.filtarPorUnico(id);
		if (result != null) {
			return Response.ok(result).build();
		} else {
			return Response.status(Response.Status.NO_CONTENT).build(); // Retorna 204 se não encontrado
		}
	}

	@GET
	@Path("/idbam/{bam}/setor/{setor}/leito/{leito}/quem/{quem}")
	public void alterarLeito(@PathParam("bam") int bam, @PathParam("setor") int setor, @PathParam("leito") int leito, @PathParam("quem") String quem) {
		novointernadoService.alterarLeito(bam, setor, leito, quem);
	}

	@PATCH
	@Path("/{bam}/leito/setor/{setor}/leito/{leito}/quem/{quem}")
	public Response atualizarLeito(@PathParam("bam") int bam, @PathParam("setor") int setor, @PathParam("leito") int leito, @PathParam("quem") String quem) {
		novointernadoService.alterarLeito(bam, setor, leito, quem);
		return Response.status(204).build();
	}
	
	@GET
	@Path("/idbamalta/{bam}/acao/{acao}/alta/{alta}/quem/{quem}/motivo/{motivo}/idn/{idnovo}")
	public void alterarAltaDoPaciente(@PathParam("bam") int bam, @PathParam("acao") LocalDate acao, @PathParam("alta") boolean alta, @PathParam("quem") String quem, @PathParam("motivo") String motivo, @PathParam("idnovo") int idnovo) {
		novointernadoService.alterarAltaDoPaciente(bam, acao, alta, quem, motivo, idnovo);
	}

	@PATCH
	@Path("/{bam}/alta/acao/{acao}/alta/{alta}/quem/{quem}/motivo/{motivo}/idn/{idnovo}")
	public Response atualizarAltaDoPaciente(@PathParam("bam") int bam, @PathParam("acao") LocalDate acao, @PathParam("alta") boolean alta, @PathParam("quem") String quem, @PathParam("motivo") String motivo, @PathParam("idnovo") int idnovo) {
		novointernadoService.alterarAltaDoPaciente(bam, acao, alta, quem, motivo, idnovo);
		return Response.status(204).build();
	}

	@POST
	public Response salvarNovoInternado(NovoInternadoDto dto) {
		NovoInternado novointernado = null;
		novointernado = novointernadoService.salvarNovoInternado(dto);
		return Response.ok(novointernado).status(201).build();
	}
	
	@GET
	@Path("/idbamdeletar/{bam}")
	public void apagarAlocacao(@PathParam("bam") int bam) {
		novointernadoService.deletarAlocacao(bam);
	}

	@DELETE
	@Path("/{bam}/alocacao")
	public Response removerAlocacao(@PathParam("bam") int bam) {
		novointernadoService.deletarAlocacao(bam);
		return Response.status(204).build();
	}

	@PATCH
	@Path("/{bam}/cancelamento-alta")
	public Response cancelarAltaNoCenso(@PathParam("bam") int bam) {
		if (!novointernadoService.cancelarAltaNoCenso(bam)) {
			return Response.status(Response.Status.CONFLICT)
					.entity("Alta não encontrada ou paciente já está ativo no Censo.")
					.build();
		}
		return Response.noContent().build();
	}
	
	
	/*
	 * @PUT
	 * 
	 * @Path("/atualizar/{id}") public Response atualizarRegistro(@PathParam("id")
	 * int id, RegistroDto dto) { registroService.atualizarRegistro(id, dto); return
	 * Response.status(204).build(); }
	 */
}
