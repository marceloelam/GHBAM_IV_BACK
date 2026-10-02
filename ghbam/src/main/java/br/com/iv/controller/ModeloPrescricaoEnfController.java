package br.com.iv.controller;

import java.text.ParseException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.DELETE;

import br.com.iv.dto.ModeloPrescricaoEnfDto;
import br.com.iv.entity.ModeloPrescricaoEnf;
import br.com.iv.service.ModeloPrescricaoEnfService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@Path("modeloprescricaoenf")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ModeloPrescricaoEnfController {

	@Inject
	ModeloPrescricaoEnfService modeloPrescricaoEnfService;

	@GET
	@Path("/titulo/{nome}")
	public Response xlistarModeloDePrescricao(@PathParam("nome") String ynome) {
		ModeloPrescricaoEnfDto dto = new ModeloPrescricaoEnfDto();
		List<ModeloPrescricaoEnf> modeloPrescricaoEnf = modeloPrescricaoEnfService.filtarPorModeloPrescricao(ynome, dto);
		return Response.ok(modeloPrescricaoEnf).build();
	}

	@POST
	public Response salvarModeloPrescricao(ModeloPrescricaoEnfDto dto,
			@Context ContainerRequestContext requestContext) {
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (user != null && user.nome() != null && !user.nome().isBlank()) {
			// O criador e uma informacao de seguranca: nunca confiar no valor enviado
			// pelo navegador para decidir quem podera remover o modelo depois.
			dto.setCriador(user.nome());
		}
		ModeloPrescricaoEnf modelo = null;
		try {
			modelo = modeloPrescricaoEnfService.salvarModeloPrescricao(dto);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return Response.ok(modelo).status(201).build();
	}

	@DELETE
	@Path("/remove/{id}")
	public Response removerModeloPrescricao(@PathParam("id") int id, ModeloPrescricaoEnfDto dto,
			@Context ContainerRequestContext requestContext) {
		ModeloPrescricaoEnf modelo = ModeloPrescricaoEnf.findById(id);
		if (modelo == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		// So quem criou o modelo pode apaga-lo (mesma regra da evolucao).
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (!ModeloEvolucaoEnfController.ehCriador(modelo.getCriador(), user)) {
			return Response.status(Response.Status.FORBIDDEN)
					.entity("{\"erro\":\"Apenas quem criou o modelo pode removê-lo.\"}")
					.build();
		}
		modeloPrescricaoEnfService.removeModeloPrescricao(id);
		return Response.status(204).build();
	}

	}
