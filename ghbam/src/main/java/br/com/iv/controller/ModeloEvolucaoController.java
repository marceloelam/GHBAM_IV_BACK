package br.com.iv.controller;

import java.text.ParseException;
import java.util.List;

import br.com.iv.dto.ModeloEvolucaoDto;
import br.com.iv.entity.ModeloEvolucao;
import br.com.iv.service.ModeloEvolucaoService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
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
@Path("modeloevolucao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ModeloEvolucaoController {

	@Inject
	ModeloEvolucaoService modeloEvolucaoService;
	
	@GET
	@Path("/titulo/{nome}")
	public Response xlistarModeloDeEvolucao(@PathParam("nome") String ynome) {
		ModeloEvolucaoDto dto = new ModeloEvolucaoDto();
		List<ModeloEvolucao> modeloEvolucao = modeloEvolucaoService.filtarPorModeloEvolucao(ynome, dto);
		return Response.ok(modeloEvolucao).build();
	}

	@POST
	public Response salvarModeloEvolucao(ModeloEvolucaoDto dto,
			@Context ContainerRequestContext requestContext) {
		AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (user != null && user.nome() != null && !user.nome().isBlank()) {
			dto.setCriador(user.nome());
		}
		ModeloEvolucao modelo = null;
		try {
			modelo = modeloEvolucaoService.salvarModeloEvolucao(dto);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return Response.ok(modelo).status(201).build();
	}

	@DELETE
	@Path("/remove/{id}")
	public Response removerModeloEvolucao(@PathParam("id") int id, ModeloEvolucaoDto dto,
			@Context ContainerRequestContext requestContext) {
		ModeloEvolucao modelo = ModeloEvolucao.findById(id);
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
		modeloEvolucaoService.removeModeloEvolucao(id);
		return Response.status(204).build();
	}
	
	}
