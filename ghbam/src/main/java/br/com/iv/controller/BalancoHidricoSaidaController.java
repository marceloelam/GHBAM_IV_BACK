package br.com.iv.controller;

import java.util.List;

import br.com.iv.controller.EscalaPacienteController.ErrorResponse;
import br.com.iv.dto.BalancoHibricoSaidaDto;
import br.com.iv.entity.BalancoHidricoSaida;
import br.com.iv.service.BalancoHidricoSaidaService;
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

@Path("backend_controle_saida")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BalancoHidricoSaidaController {

	@Inject
	BalancoHidricoSaidaService balancoHidricoSaidaService;

	//------------------------------ Saida Balanço Hídrico --------------------------------//
	
	@GET
	@Path("/saida/{bam}/dt/{data}")
	public Response xlistarSaidaBalancoHidrico(@PathParam("bam") String bam, @PathParam("data") String data) {
		List<BalancoHidricoSaida> lista_Saidas = balancoHidricoSaidaService.filtarPorBamData(bam, data);
		return Response.ok(lista_Saidas).build();
	}
	
	
	@POST
	public Response salvarSaidaBalancoHidrico(BalancoHibricoSaidaDto dto) {
		try {
			BalancoHidricoSaida balancoHidricoSaida = balancoHidricoSaidaService.salvarBalancoHidricoSaida(dto);
			return Response.ok(balancoHidricoSaida).status(Response.Status.CREATED).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(e.getMessage())).build();
		} catch (Exception e) {
			return Response.serverError().entity(new ErrorResponse("Erro ao salvar Saida do balanço hídrico"))
					.build();
		}
	}

	@PUT
	@Path("{id}")
	public Response atualizarSaidaBalanco(@PathParam("id") String id, BalancoHibricoSaidaDto dto) {
		BalancoHidricoSaida balancoHidricoSaida = balancoHidricoSaidaService.alterarBalancoHidricoSaida(id, dto);
		return Response.ok(balancoHidricoSaida).build();
	}

	@DELETE
	@Path("{id}")
	public Response removerSaidaBalanco(@PathParam("id") String id, BalancoHibricoSaidaDto dto) {
		balancoHidricoSaidaService.deletarBalancoHidricoSaida(id);
		return Response.status(204).build();
	}
	
	
	

}
