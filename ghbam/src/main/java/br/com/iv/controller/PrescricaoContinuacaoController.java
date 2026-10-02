package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.BalancoHibricoSaidaDto;
import br.com.iv.dto.PrescricaoContinuacaoDto;
import br.com.iv.entity.PrescricaoContinuacao;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.PrescricaoContinuacaoService;
import jakarta.ws.rs.container.ContainerRequestContext;

@Path("prescricaocontinuacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PrescricaoContinuacaoController {

	@Inject
	PrescricaoContinuacaoService prescricaoContinuacaoService;

	@Context
	ContainerRequestContext requestContext;

	@GET
	@Path("/bam/{bam}")
	public Response listarPrescricaoContinuacaoPaciente(@PathParam("bam") int bam) {
		List<PrescricaoContinuacao> PrescricaoContinuacaoUnidade = prescricaoContinuacaoService.filtarPorBam(bam);
		return Response.ok(PrescricaoContinuacaoUnidade).build();
	}
	
	@GET
	@Path("/bam2/{bam}/dt/{data}")
	public Response listarPrescricaoContinuacaoPacienteDt(@PathParam("bam") int bam, @PathParam("data") String data) {
		List<PrescricaoContinuacao> PrescricaoContinuacaoUnidade = prescricaoContinuacaoService.filtarPorBam2(bam,data);
		return Response.ok(PrescricaoContinuacaoUnidade).build();
	}


	@GET
	@Path("/bamultimo/{bam}")
	public Response listarUltimaPrescricaoContinuacaoPaciente(@PathParam("bam") int bam) {
		PrescricaoContinuacao PrescricaoContinuacaoUnidade = prescricaoContinuacaoService.filtarUltimaPorBam(bam);
		return Response.ok(PrescricaoContinuacaoUnidade).build();
	}

	@POST
	public Response salvarPrescricaoContinuacaoPaciente(PrescricaoContinuacaoDto dto) {
		PrescricaoContinuacao PrescricaoContinuacaoPaciente = prescricaoContinuacaoService.salvarEvolucao(dto, usuarioAutenticado());
		return Response.ok(PrescricaoContinuacaoPaciente).status(201).build();
	}

	@PUT
	@Path("{id}")
	public Response atualizarPrescricaoContinuacaoPaciente(@PathParam("id") String id, PrescricaoContinuacaoDto dto) {
		try {
			prescricaoContinuacaoService.prescricaoContinuacaoAltera(id, dto);
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
		}
	}

	@DELETE
	@Path("{id}")
	public Response removerPrescricaoContinuacao(@PathParam("id") int id) {
		try {
			prescricaoContinuacaoService.removePaciente(id, usuarioAutenticado());
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
		} catch (SecurityException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
		}
	}

	private AuthenticatedUser usuarioAutenticado() {
		Object usuario = requestContext.getProperty("ghbam.user");
		if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
			throw new NotAuthorizedException("Bearer");
		}
		return autenticado;
	}

}
