package br.com.iv.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.VisitantesDto;
import br.com.iv.entity.Visitantes;
import br.com.iv.service.VisitantesService;

@Path("visitantes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VisitantesController {

	@Inject
	VisitantesService visitantesService;

	@POST
	public Response salvarVisitantes(VisitantesDto dto) {
		try {
			Visitantes visitantes = visitantesService.salvarLog(dto);
			return Response.status(Response.Status.CREATED).entity(visitantes).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT)
					.entity(Map.of("mensagem", e.getMessage()))
					.build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(Map.of("mensagem", e.getMessage()))
					.build();
		}
	}

	@GET
	@Path("/unidade/{yunidade}/inicio/{dt_inicio}/fim/{dt_fim}")
	public Response xlistarVisitantesPorDatas(@PathParam("yunidade") String yunidade,
			@PathParam("dt_inicio") String dt_inicio, @PathParam("dt_fim") String dt_fim) {

		LocalDate localDate1 = LocalDate.parse(dt_inicio);
		LocalDate localDate2 = LocalDate.parse(dt_fim);

		List<Visitantes> visitantes = visitantesService.filtrarPelaUnidadeEDataVisita(yunidade, localDate1, localDate2);
		return Response.ok(visitantes).build();
	}

	
	@GET
	@Path("/bam/{ybam}")
	public Response xlistarVisitantesPorBam(@PathParam("ybam") String ybam) {
		List<Visitantes> visitantes = visitantesService.filtrarPelobamVisita(ybam);
		return Response.ok(visitantes).build();
	}

	
	
}
