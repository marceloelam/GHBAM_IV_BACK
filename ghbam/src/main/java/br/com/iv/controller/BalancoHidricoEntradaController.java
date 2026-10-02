package br.com.iv.controller;

import java.util.List;

import br.com.iv.controller.EscalaPacienteController.ErrorResponse;
import br.com.iv.dto.BalancoHibricoEntradaDto;
import br.com.iv.dto.Cid1Dto;
import br.com.iv.dto.PacientesDto;
import br.com.iv.dto.PrescricaoContinuacaoDto;
import br.com.iv.entity.BalancoHidricoEntrada;
import br.com.iv.entity.Cid1;
import br.com.iv.service.BalancoHidricoEntradaService;
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

@Path("backend_controle_entrada")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BalancoHidricoEntradaController {

	@Inject
	BalancoHidricoEntradaService balancoHidricoEntradaService;

	//------------------------------ Entrada Balanço Hídrico --------------------------------//
	
	@GET
	@Path("/entrada/{bam}/dt/{data}")
	public Response xlistarEntradaBalancoHidrico(@PathParam("bam") String bam, @PathParam("data") String data) {
		List<BalancoHidricoEntrada> lista_entradas = balancoHidricoEntradaService.filtarPorBamData(bam, data);
		return Response.ok(lista_entradas).build();
	}
	
	
	@POST
	public Response salvarEntradaBalancoHidrico(BalancoHibricoEntradaDto dto) {
		try {
			BalancoHidricoEntrada balancoHidricoEntrada = balancoHidricoEntradaService.salvarBalancoHidricoEntrada(dto);
			return Response.ok(balancoHidricoEntrada).status(Response.Status.CREATED).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(e.getMessage())).build();
		} catch (Exception e) {
			return Response.serverError().entity(new ErrorResponse("Erro ao salvar entrada do balanço hídrico"))
					.build();
		}
	}

	@PUT
	@Path("{id}")
	public Response atualizarEntradaBalanco(@PathParam("id") String id, BalancoHibricoEntradaDto dto) {
		BalancoHidricoEntrada balancoHidricoEntrada = balancoHidricoEntradaService.alterarBalancoHidricoEntrada(id, dto);
		return Response.ok(balancoHidricoEntrada).build();
	}

	@DELETE
	@Path("{id}")
	public Response removerEntradaBalanco(@PathParam("id") String id, BalancoHibricoEntradaDto dto) {
		balancoHidricoEntradaService.deletarBalancoHidricoEntrada(id);
		return Response.status(204).build();
	}
	
	
	

}
