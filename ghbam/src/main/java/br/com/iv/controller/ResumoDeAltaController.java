package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PacientesDto;
import br.com.iv.dto.ResumoDeAltaDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.ResumoDeAlta;
import br.com.iv.service.ResumoDeAltaService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

@Path("resumodealta")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ResumoDeAltaController {

	@Context
	ContainerRequestContext requestContext;

	@Inject
	ResumoDeAltaService  resumoDeAltaService;
	
	@GET
	public Response listarResumoDeAlta() {
		List<ResumoDeAlta> resumo = resumoDeAltaService.listarTodosResumos();
		return Response.ok(resumo).build();
	}

	@GET
	@Path("/cancelados")
	public Response listarCancelados(
			@QueryParam("nome") String nome,
			@QueryParam("bam") String bam,
			@QueryParam("prontuario") String prontuario) {
		Object principal = requestContext == null ? null : requestContext.getProperty("ghbam.user");
		if (!(principal instanceof AuthenticatedUser user) || user.unidade() == null || user.unidade().isBlank()) {
			return Response.status(Response.Status.FORBIDDEN).entity("Unidade do usuario nao identificada.").build();
		}
		return Response.ok(resumoDeAltaService.listarCanceladosPorUnidade(user.unidade(), nome, bam, prontuario)).build();
	}
	
	@GET
	@Path("/idbam/{idbam}")
	public ResumoDeAlta listarPorUnicoBam(@PathParam("idbam") int idbam) {
		return resumoDeAltaService.filtarPorUnicoBam(idbam);
	}

	@POST
	public Response salvarResumoDeAlta(ResumoDeAltaDto dto) {
		ResumoDeAlta resumoDeAlta = resumoDeAltaService.salvarResumoDeAlta(dto); 
		return Response.ok(resumoDeAlta).status(201).build();
	}
	
	@PUT
	public Response atualizarResumoDeAlta(ResumoDeAltaDto dto) {
		ResumoDeAlta resumoDeAlta = resumoDeAltaService.salvarResumoDeAlta(dto); 
		return Response.ok(resumoDeAlta).status(201).build();
	}
	
	@PUT
	@Path("{id}")
	public Response atualizarResumoDeAlta(@PathParam("id") int id, ResumoDeAltaDto dto) {
		resumoDeAltaService.atualizarResumo(id, dto);
		return Response.status(204).build();
	}
	
	
	}
