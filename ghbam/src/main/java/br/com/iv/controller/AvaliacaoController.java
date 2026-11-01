package br.com.iv.controller;

import java.io.StringReader;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import br.com.iv.dto.AvaliacaoDto;
import br.com.iv.entity.Avaliacao;
import br.com.iv.service.AvaliacaoService;
import br.com.iv.util.ClassificacaoRiscoResponse;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
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

@Path("avaliacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AvaliacaoController {

	@Inject
	AvaliacaoService avaliacaoService;

	@GET
	public Response listarAvaliacao() {
		List<Avaliacao> avaliacao = avaliacaoService.listarTodosOsAvaliacaos();
		return Response.ok(avaliacao).build();
	}

	@GET
	@Path("/id/{id}")
	public Avaliacao listarPorID(@PathParam("id") int id) {
		return avaliacaoService.listarPorID(id);
	}

	@GET
	@Path("/controle/{controle}")
	public Avaliacao listarPorControle(@PathParam("controle") String controle) {
		return avaliacaoService.listarPorControle(controle);
	}

	@GET
	@Path("/paciente/{idpaciente}/ultima")
	public Response ultimaPorPaciente(@PathParam("idpaciente") int idpaciente) {
		Avaliacao avaliacao = avaliacaoService.ultimaPorPaciente(idpaciente);
		return Response.ok(avaliacao).build();
	}

	@GET
	@Path("/unidadeclass/{unidade}/data/{dataRequerida}")
	public Response listarClassificacaoDeRisco(@PathParam("unidade") String unidade,
			@PathParam("dataRequerida") String dataRequerida) {
		List<Avaliacao> classificacao = avaliacaoService.listarPorClassificacao(unidade, dataRequerida);
		return Response.ok(classificacao).build();
	}

	// Novo endpoint completo
	@GET
	@Path("/completo/{unidade}/data/{dataRequerida}")
	public Response riscoCompletoPorUnidade(@PathParam("unidade") String unidade,
			@PathParam("dataRequerida") String dataRequerida) {
		try {
			Map<String, Map<String, Long>> contagem = avaliacaoService.listarClassificacaoDeRisco(unidade,
					dataRequerida);

			ClassificacaoRiscoResponse response = new ClassificacaoRiscoResponse(contagem.get("dia"),
					contagem.get("semana"), contagem.get("mes"), contagem.get("ano"));

			return Response.ok(response).build();
		} catch (DateTimeParseException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity("Formato de data inválido. Use yyyy-MM-dd")
					.build();
		} catch (Exception e) {
			return Response.serverError().entity("Ocorreu um erro ao processar a requisição").build();
		}
	}

	@GET
	@Path("/unidadever/{unidade}/data/{data}")
	public Response contagemDeAtendimentos(@PathParam("unidade") String unidade, @PathParam("data") String data) {
		long resultado = avaliacaoService.contagemDeAtendimentos(unidade, data);
		return Response.ok(resultado).build();
	}
	
	@GET
	@Path("/unidadeverhoje/{unidade}/data/{data}")
	public Response contagemDeAtendimentosHoje(@PathParam("unidade") String unidade, @PathParam("data") String data) {
		long resultado = avaliacaoService.contagemDeAtendimentosHoje(unidade, data);
		return Response.ok(resultado).build();
	}

	@GET
	@Path("/unidadeversetorhoje/{unidade}/data/{data}")
	public Response contagemDeAtendimentosHojePorSetor(@PathParam("unidade") String unidade, @PathParam("data") String data) {
		return Response.ok(avaliacaoService.contagemDeAtendimentosHojePorSetor(unidade, data)).build();
	}

	@GET
	@Path("/unidadeversetorsemana/{unidade}/data/{data}")
	public Response contagemDeAtendimentosSemanaPorSetor(@PathParam("unidade") String unidade, @PathParam("data") String data) {
		return Response.ok(avaliacaoService.contagemDeAtendimentosSemanaPorSetor(unidade, data)).build();
	}

	@GET
	@Path("/unidadeversetormes/{unidade}/data/{data}")
	public Response contagemDeAtendimentosMesPorSetor(@PathParam("unidade") String unidade, @PathParam("data") String data) {
		return Response.ok(avaliacaoService.contagemDeAtendimentosMesPorSetor(unidade, data)).build();
	}

	@GET
	@Path("/unidadeversetorano/{unidade}/data/{data}")
	public Response contagemDeAtendimentosAnoPorSetor(@PathParam("unidade") String unidade, @PathParam("data") String data) {
		return Response.ok(avaliacaoService.contagemDeAtendimentosAnoPorSetor(unidade, data)).build();
	}
	
	

	@GET
	@Path("/avaliado/{idavaliacao}/avaliacao/{num}")
	public void alterarAvaliado(@PathParam("idavaliacao") int idavaliacao, @PathParam("num") int num) {
		alterarAvaliadoPorId(idavaliacao, num);
	}

	@PATCH
	@Path("/{idavaliacao}/avaliado/{num}")
	public void alterarAvaliadoPatch(@PathParam("idavaliacao") int idavaliacao, @PathParam("num") int num) {
		alterarAvaliadoPorId(idavaliacao, num);
	}

	private void alterarAvaliadoPorId(int idavaliacao, int num) {
		avaliacaoService.alterarAvaliado(idavaliacao, num);
	}

	@GET
	@Path("/atualizarespera/{idavaliacao}/opcao/{num}")
	public void removerEsperaMedico(@PathParam("idavaliacao") int idavaliacao, @PathParam("num") String num) {
		removerEsperaMedicoPorId(idavaliacao, num);
	}

	@PATCH
	@Path("/{idavaliacao}/espera/opcao/{num}")
	public void removerEsperaMedicoPatch(@PathParam("idavaliacao") int idavaliacao, @PathParam("num") String num) {
		removerEsperaMedicoPorId(idavaliacao, num);
	}

	private void removerEsperaMedicoPorId(int idavaliacao, String num) {
		avaliacaoService.removerEsperaMedico(idavaliacao, num);
	}

	@POST
	@Path("/atualizaranaminese/{idavaliacao}/anamnese")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response atualizarAnamnese(@PathParam("idavaliacao") int idavaliacao,
	                              String body) {
	    return atualizarAnamnesePorBody(idavaliacao, body);
	}

	@PATCH
	@Path("/{idavaliacao}/anamnese")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response atualizarAnamnesePatch(@PathParam("idavaliacao") int idavaliacao,
	                              String body) {
	    return atualizarAnamnesePorBody(idavaliacao, body);
	}

	private Response atualizarAnamnesePorBody(int idavaliacao, String body) {
	    if (idavaliacao <= 0 || body == null || body.isBlank()) {
	        return Response.status(Response.Status.BAD_REQUEST)
	                .entity(Map.of("message", "Informe uma avaliação válida e o texto da anamnese."))
	                .build();
	    }

	    final String anamnese;
	    try (var reader = Json.createReader(new StringReader(body))) {
	        JsonObject json = reader.readObject();
	        if (!json.containsKey("texto") || json.isNull("texto")
	                || json.get("texto").getValueType() != jakarta.json.JsonValue.ValueType.STRING) {
	            return Response.status(Response.Status.BAD_REQUEST)
	                    .entity(Map.of("message", "O campo texto deve ser uma string."))
	                    .build();
	        }
	        anamnese = json.getString("texto");
	    } catch (JsonException e) {
	        return Response.status(Response.Status.BAD_REQUEST)
	                .entity(Map.of("message", "O corpo da requisição deve ser um JSON válido."))
	                .build();
	    }

	    if (anamnese.length() > 8000) {
	        return Response.status(Response.Status.BAD_REQUEST)
	                .entity(Map.of("message", "A anamnese deve ter no máximo 8.000 caracteres."))
	                .build();
	    }

	    return avaliacaoService.atualizarAnamnese(idavaliacao, anamnese) > 0
	            ? Response.noContent().build()
	            : Response.status(Response.Status.NOT_FOUND)
	                    .entity(Map.of("message", "Avaliação não encontrada."))
	                    .build();
	}

	@PATCH
	@Path("/{idavaliacao}/comentario-acrescimo")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response acrescentarComentario(@PathParam("idavaliacao") int idavaliacao, String body) {
		JsonObject json = Json.createReader(new StringReader(body)).readObject();
		String texto = json.getString("texto", "").trim();
		if (texto.isBlank()) {
			return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", "Observacao obrigatoria.")).build();
		}
		String comentario = avaliacaoService.acrescentarComentario(idavaliacao, texto);
		return Response.ok(Map.of("comentario", comentario)).build();
	}

	@GET
	@Path("/atualizarcor/{idavaliacao}/idcor/{idcor}/cor/{cor}")
	public void atualizarCor(@PathParam("idavaliacao") int idavaliacao, @PathParam("idcor") int idcor,
			@PathParam("cor") String cor) {
		atualizarCorPorId(idavaliacao, idcor, cor);
	}

	@PATCH
	@Path("/{idavaliacao}/cor/idcor/{idcor}/descricao/{cor}")
	public void atualizarCorPatch(@PathParam("idavaliacao") int idavaliacao, @PathParam("idcor") int idcor,
			@PathParam("cor") String cor) {
		atualizarCorPorId(idavaliacao, idcor, cor);
	}

	private void atualizarCorPorId(int idavaliacao, int idcor, String cor) {
		avaliacaoService.atualizarCor(idavaliacao, idcor, cor);
	}

	@POST
	public Response salvarAvaliacao(AvaliacaoDto dto) {
		Avaliacao avaliacao = avaliacaoService.salvarAvaliacao(dto);
		return Response.ok(avaliacao).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarAvaliacao(@PathParam("id") int id, AvaliacaoDto dto) {
		avaliacaoService.atualizarAvaliacao(id, dto);
		return Response.status(204).build();
	}

}
