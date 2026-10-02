package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PrescricaoDto;
import br.com.iv.entity.Prescricao;
import br.com.iv.service.PrescricaoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("prescricao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PrescricaoController {

	@Inject
	PrescricaoService prescricaoService;

	@jakarta.ws.rs.core.Context
	jakarta.ws.rs.container.ContainerRequestContext requestContext;


	@GET
	@Path("/idp/{id}")
	public Response PrescricaoPacienteID(@PathParam("id") int id) {
		Prescricao prescricaoPacienteUnidade = prescricaoService.filtarPorId(id);
		return Response.ok(prescricaoPacienteUnidade).build();
	}
	
	
	@GET
	@Path("/bam/{bam}/inicio/{inicio}/fim/{fim}")
	public Response listarPrescricao(@PathParam("bam") int bam, @PathParam("inicio") String inicio, @PathParam("fim") String fim) {
		List<Prescricao> prescricaoUnidade = prescricaoService.filtarPorBam(bam, inicio, fim);
		return Response.ok(prescricaoUnidade).build();
	}

	@GET
	@Path("/bam/{bam}/inicio/{inicio}/fim/{fim}/tipo/{tipo}")
	public Response listarPrescricao(@PathParam("bam") int bam, @PathParam("inicio") String inicio, @PathParam("fim") String fim, @PathParam("tipo") String tipo) {
		List<Prescricao> prescricaoUnidade = prescricaoService.filtarPorBam(bam, inicio, fim, tipo);
		return Response.ok(prescricaoUnidade).build();
	}
	
	@GET
	@Path("/bamultimo/{bam}")
	public Response listarUltimaPrescricao(@PathParam("bam") int bam) {
		Prescricao prescricaoUnidade = prescricaoService.filtarUltimaPorBam(bam);
		return Response.ok(prescricaoUnidade).build();
	}
	
	@GET
	@Path("/bam2/{bam}")
	public Response listarEvolucaoPaciente2(@PathParam("bam") int bam) {
		List<Prescricao> prescricaoPacienteUnidade2 = prescricaoService.filtarPorBam2(bam);
		return Response.ok(prescricaoPacienteUnidade2).build();
	}

	@GET
	@Path("/inativar/{id}")
	public Response inativar(@PathParam("id") int id) {
		return inativarPrescricao(id);
	}

	@PATCH
	@Path("/{id}/inativacao")
	public Response inativarPorPatch(@PathParam("id") int id) {
		return inativarPrescricao(id);
	}

	private Response inativarPrescricao(int id) {
		try {
			prescricaoService.inativar(id);
			return Response.noContent().build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
		}
	}

	
	
	@GET
	@Path("/bam/{bam}/minhas")
	public Response listarMinhasPrescricoesDoBam(@PathParam("bam") int bam) {
		br.com.iv.security.AuthenticatedUser usuario = usuarioAutenticado();
		List<Prescricao> minhas = prescricaoService.listarMinhasPorBam(bam, usuario);
		return Response.ok(minhas).build();
	}

	@POST
	public Response salvarPrescricao(PrescricaoDto dto) {
		br.com.iv.security.AuthenticatedUser usuario = usuarioAutenticado();
		Prescricao prescricao = prescricaoService.salvarPrescricao(dto, usuario);
		return Response.ok(prescricao).status(201).build();
	}

	// Retificacao: somente o autor original, dentro do prazo de 4h, e um
	// registro so pode ser retificado uma vez (validado no service).
	@POST
	@Path("/{id}/retificar")
	public Response retificarPrescricao(@PathParam("id") int id, PrescricaoDto dto) {
		br.com.iv.security.AuthenticatedUser usuario = usuarioAutenticado();
		try {
			Prescricao nova = prescricaoService.retificar(id, dto, usuario);
			return Response.status(Response.Status.CREATED).entity(nova).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(erro(e.getMessage())).build();
		} catch (SecurityException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(erro(e.getMessage())).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(erro(e.getMessage())).build();
		}
	}

	private br.com.iv.security.AuthenticatedUser usuarioAutenticado() {
		Object usuario = requestContext.getProperty("ghbam.user");
		if (!(usuario instanceof br.com.iv.security.AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
			throw new jakarta.ws.rs.NotAuthorizedException("Bearer");
		}
		return autenticado;
	}

	private String erro(String mensagem) {
		String seguro = mensagem == null ? "" : mensagem.replace("\\", "\\\\").replace("\"", "\\\"");
		return "{\"erro\":\"" + seguro + "\"}";
	}

	
	@POST
	@Path("/aprazamento/{idprescricao}")
	public Response  atualizarAprazamento(@PathParam("idprescricao") int idprescricao, Prescricao dto) {
		try {
			prescricaoService.prescricao(idprescricao, dto);
			return Response.status(204).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
		}
	}
	

}
