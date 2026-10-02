package br.com.iv.controller;

import java.util.List;

import br.com.iv.controller.EscalaPacienteController.ErrorResponse;
import br.com.iv.dto.BalancoHibricoResumoDto;
import br.com.iv.entity.BalancoHidricoEntrada;
import br.com.iv.entity.ResumoBalancoHidrico;

import br.com.iv.service.ResumoBalancoHidricoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("backend_resumo_balanco")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ResumoBalancoHidricoController {

	@Inject
	ResumoBalancoHidricoService balancoHidricoResumoService;

	//------------------------------ Resumo Balanço Hídrico --------------------------------//
	
	@GET
	@Path("/resumo/{bam}/dt/{data}")
	public Response xlistarEntradaBalancoHidrico(@PathParam("bam") String bam, @PathParam("data") String data) {
		List<ResumoBalancoHidrico> lista_entradas = balancoHidricoResumoService.filtarPorBamData(bam, data);
		return Response.ok(lista_entradas).build();
	}
	
	@POST
	public Response salvarResumoBalancoHidrico(BalancoHibricoResumoDto dto) {
		try {
			ResumoBalancoHidrico resumoBalancoHidrico = balancoHidricoResumoService.salvarResumoBalancoHidrico(dto);
			return Response.ok(resumoBalancoHidrico).status(Response.Status.CREATED).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(e.getMessage())).build();
		} catch (Exception e) {
			return Response.serverError().entity(new ErrorResponse("Erro ao salvar Resumo do balanço hídrico"))
					.build();
		}
	}

	@PUT
	@Path("{id}")
	public Response atualizarResumoBalanco(@PathParam("id") String id, BalancoHibricoResumoDto dto) {
		balancoHidricoResumoService.alterarResumoBalancoHidrico(id, dto);
		return Response.status(204).build();
	}

	
	

}
