package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.EvolucaoPacienteEnfDto;
import br.com.iv.entity.EvolucaoPacienteEnf;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.EvolucaoPacienteEnfService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("evolucaopacienteenf")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvolucaoPacienteEnfController {

	@Inject
	EvolucaoPacienteEnfService evolucaoPacienteEnfService;

	@Context
	ContainerRequestContext requestContext;


	@GET
	@Path("/bam/{bam}/inicio/{inicio}/fim/{fim}/tipo/{tipo}")
	public Response listarEvolucaoPaciente(@PathParam("bam") int bam, @PathParam("inicio") String inicio, @PathParam("fim") String fim, @PathParam("tipo") String tipo) {
		List<EvolucaoPacienteEnf> evolucaoPacienteUnidade = evolucaoPacienteEnfService.filtarPorBam(bam, inicio, fim, tipo);
		return Response.ok(evolucaoPacienteUnidade).build();
	}

	// Bare /bam/{bam}: varios editores no frontend chamam assim esperando TODAS
	// as evolucoes de enfermagem do bam, sem filtro de data/tipo.
	@GET
	@Path("/bam/{bam}")
	public Response listarEvolucaoPacienteEnfBamSimples(@PathParam("bam") int bam) {
		List<EvolucaoPacienteEnf> evolucaoPacienteUnidade = evolucaoPacienteEnfService.filtarTodasPorBam(bam);
		return Response.ok(evolucaoPacienteUnidade).build();
	}

	// Bare + tipo (sem data): usado pela tela que carrega tudo do bam de uma
	// vez, separado por enfermagem (tipo=1) ou multidisciplinar (qualquer
	// outro valor), e deixa a ordenacao por conta do frontend.
	@GET
	@Path("/bam/{bam}/tipo/{tipo}")
	public Response listarEvolucaoPacienteEnfBamTipo(@PathParam("bam") int bam, @PathParam("tipo") String tipo) {
		List<EvolucaoPacienteEnf> evolucaoPacienteUnidade = evolucaoPacienteEnfService.filtarTodasPorBamETipo(bam, tipo);
		return Response.ok(evolucaoPacienteUnidade).build();
	}
		@GET
	@Path("/bamultimo/{bam}")
	public Response listarUltimaEvolucaoPaciente(@PathParam("bam") int bam) {
		// Somente a ultima evolucao do proprio profissional autenticado: a de
		// outro profissional nao pode ser importada para edicao.
		AuthenticatedUser usuario = usuarioAutenticado();
		EvolucaoPacienteEnf evolucaoPacienteUnidade = evolucaoPacienteEnfService.buscarMinhaUltimaPorBam(bam, usuario);
		if (evolucaoPacienteUnidade == null) {
			return Response.noContent().build();
		}
		return Response.ok(evolucaoPacienteUnidade).build();
	}

	// Somente as evolucoes do bam feitas pelo profissional autenticado - usado
	// pela tela de retificacao ("Minhas evolucoes").
	@GET
	@Path("/bam/{bam}/minhas")
	public Response listarMinhasEvolucoesDoBam(@PathParam("bam") int bam) {
		AuthenticatedUser usuario = usuarioAutenticado();
		List<EvolucaoPacienteEnf> minhas = evolucaoPacienteEnfService.listarMinhasPorBam(bam, usuario);
		return Response.ok(minhas).build();
	}

	@POST
	public Response salvarEvolucaoPaciente(EvolucaoPacienteEnfDto dto) {
		AuthenticatedUser usuario = usuarioAutenticado();
		EvolucaoPacienteEnf evolucaoPaciente = evolucaoPacienteEnfService.salvarEvolucao(dto, usuario);
		return Response.ok(evolucaoPaciente).status(201).build();
	}

	// Retificacao: somente o autor original, dentro do prazo de 4h, e um
	// registro so pode ser retificado uma vez (validado no service).
	@POST
	@Path("/{id}/retificar")
	public Response retificarEvolucaoPaciente(@PathParam("id") int id, EvolucaoPacienteEnfDto dto) {
		AuthenticatedUser usuario = usuarioAutenticado();
		try {
			EvolucaoPacienteEnf nova = evolucaoPacienteEnfService.retificar(id, dto, usuario);
			return Response.status(Response.Status.CREATED).entity(nova).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(erro(e.getMessage())).build();
		} catch (SecurityException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(erro(e.getMessage())).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(erro(e.getMessage())).build();
		}
	}

	private AuthenticatedUser usuarioAutenticado() {
		Object usuario = requestContext.getProperty("ghbam.user");
		if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
			throw new jakarta.ws.rs.NotAuthorizedException("Bearer");
		}
		return autenticado;
	}

	private String erro(String mensagem) {
		String seguro = mensagem == null ? "" : mensagem.replace("\\", "\\\\").replace("\"", "\\\"");
		return "{\"erro\":\"" + seguro + "\"}";
	}

}
