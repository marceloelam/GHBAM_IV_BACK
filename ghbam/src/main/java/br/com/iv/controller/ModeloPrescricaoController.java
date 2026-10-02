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

import br.com.iv.dto.ModeloPrescricaoDto;
import br.com.iv.entity.ModeloPrescricao;
import br.com.iv.service.ModeloPrescricaoService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@Path("modeloprescricao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ModeloPrescricaoController {

	@Inject
	ModeloPrescricaoService modeloPrescricaoService;
	
	@GET
	@Path("/titulo/{nome}")
	public Response xlistarModeloDePrescricao(@PathParam("nome") String ynome) {
		ModeloPrescricaoDto dto = new ModeloPrescricaoDto();
		List<ModeloPrescricao> modeloPrescricao = modeloPrescricaoService.filtarPorModeloPrescricao(ynome, dto);
		return Response.ok(modeloPrescricao).build();
	}

	@POST
	public Response salvarModeloPrescricao(ModeloPrescricaoDto dto,
			@Context ContainerRequestContext requestContext) {
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (user != null && user.nome() != null && !user.nome().isBlank()) {
			dto.setCriador(user.nome());
		}
		ModeloPrescricao modelo = null;
		try {
			modelo = modeloPrescricaoService.salvarModeloPrescricao(dto);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return Response.ok(modelo).status(201).build();
	}

	@DELETE
	@Path("/remove/{id}")
	public Response removerModeloPrescricao(@PathParam("id") int id, ModeloPrescricaoDto dto,
			@Context ContainerRequestContext requestContext) {
		ModeloPrescricao modelo = ModeloPrescricao.findById(id);
		if (modelo == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		// So quem criou o modelo pode apaga-lo ("Dr(a). NOME - crm : X").
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (!ModeloEvolucaoEnfController.ehCriador(modelo.getCriador(), user)) {
			return Response.status(Response.Status.FORBIDDEN)
					.entity("{\"erro\":\"Apenas quem criou o modelo pode removê-lo.\"}")
					.build();
		}
		modeloPrescricaoService.removeModeloPrescricao(id);
		return Response.status(204).build();
	}
	
	}
