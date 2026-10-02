package br.com.iv.controller;

import java.text.Normalizer;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

import br.com.iv.dto.ModeloEvolucaoEnfDto;
import br.com.iv.entity.ModeloEvolucaoEnf;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import br.com.iv.service.ModeloEvolucaoEnfService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@SuppressWarnings("unused")
@Path("modeloevolucaoenf")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ModeloEvolucaoEnfController {

	@Inject
	ModeloEvolucaoEnfService modeloEvolucaoEnfService;

	@GET
	@Path("/titulo/{nome}")
	public Response xlistarModeloDeEvolucao(@PathParam("nome") String ynome) {
		ModeloEvolucaoEnfDto dto = new ModeloEvolucaoEnfDto();
		List<ModeloEvolucaoEnf> modeloEvolucaoEnf = modeloEvolucaoEnfService.filtarPorModeloEvolucao(ynome, dto);
		return Response.ok(modeloEvolucaoEnf).build();
	}

	@POST
	public Response salvarModeloEvolucao(ModeloEvolucaoEnfDto dto,
			@Context ContainerRequestContext requestContext) {
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (user != null && user.nome() != null && !user.nome().isBlank()) {
			// O criador e uma informacao de seguranca: nunca confiar no valor enviado
			// pelo navegador para decidir quem podera remover o modelo depois.
			dto.setCriador(user.nome());
		}
		ModeloEvolucaoEnf modelo = null;
		try {
			modelo = modeloEvolucaoEnfService.salvarModeloEvolucao(dto);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return Response.ok(modelo).status(201).build();
	}

	@DELETE
	@Path("/remove/{id}")
	public Response removerModeloEvolucao(@PathParam("id") int id, ModeloEvolucaoEnfDto dto,
			@Context ContainerRequestContext requestContext) {
		ModeloEvolucaoEnf modelo = ModeloEvolucaoEnf.findById(id);
		if (modelo == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		// So quem criou o modelo pode apaga-lo. "criador" e gravado como
		// "Enf. NOME - COREN: X", entao compara pelo nome do usuario do token.
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (!ehCriador(modelo.getCriador(), user)) {
			return Response.status(Response.Status.FORBIDDEN)
					.entity("{\"erro\":\"Apenas quem criou o modelo pode removê-lo.\"}")
					.build();
		}
		modeloEvolucaoEnfService.removeModeloEvolucao(id);
		return Response.status(204).build();
	}

	static boolean ehCriador(String criador, AuthenticatedUser user) {
		if (user == null) {
			return false;
		}
		String nome = normalizar(user.nome());
		return !nome.isBlank() && normalizar(criador).contains(nome);
	}

	private static String normalizar(String valor) {
		if (valor == null) {
			return "";
		}
		return Normalizer.normalize(valor, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", " ")
				.trim()
				.replaceAll("\\s+", " ");
	}

	}
