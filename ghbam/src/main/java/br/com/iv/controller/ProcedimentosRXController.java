package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.ProcedimentosRXDto;
import br.com.iv.entity.ProcedimentosRX;
import br.com.iv.service.ProcedimentosRXService;

@Path("procedimentos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProcedimentosRXController {

	@Inject
	ProcedimentosRXService procedimentosRXService;

	@GET
	public Response listarProcedimentos() {
		List<ProcedimentosRX> procedimentosRX = procedimentosRXService.listarTodosOsProcedimentosRX();
		return Response.ok(procedimentosRX).build();
	}

	@GET
	@Path("/descricao/{descricao}/modo/{modo}/modo2/{modo2}")
	public Response xlistarProcedimentosRX(@PathParam("descricao") String descricao, @PathParam("modo") String modo,
			@PathParam("modo2") String modo2) {
		List<ProcedimentosRX> procedimentosRX = procedimentosRXService.filtarPorProcedimentoRXModos(descricao, modo,
				modo2);
		return Response.ok(procedimentosRX).build();
	}

	@GET
	@Path("/descricao/{descricao}")
	public Response xlistarProcedimentosPorDescricao(@PathParam("descricao") String descricao) {
		ProcedimentosRXDto dto = new ProcedimentosRXDto();
		List<ProcedimentosRX> procedimentosRX = procedimentosRXService.filtarPorDescricaoProcedimentos(descricao, dto);
		return Response.ok(procedimentosRX).build();
	}

	@GET
	@Path("/procedimento/{procedimento}/descricao/{descricao}")
	public Response pegarBamDoPacientePorId(@PathParam("procedimento") String procedimento,
			@PathParam("descricao") String descricao) {
		ProcedimentosRX procedimentosRX = procedimentosRXService.ProcedimentoRXId(procedimento, descricao);
		return Response.ok(procedimentosRX).build();
	}

	@GET
	@Path("/procedimento/{procedimento}/por-descricao")
	public Response buscarProcedimentoPorDescricaoSegura(@PathParam("procedimento") String procedimento,
			@QueryParam("descricao") String descricao) {
		ProcedimentosRX procedimentosRX = procedimentosRXService.ProcedimentoRXId(procedimento, descricao);
		return procedimentosRX == null
				? Response.status(Response.Status.NOT_FOUND).build()
				: Response.ok(procedimentosRX).build();
	}

	@GET
	@Path("/modo/{modo}")
	public Response xlistarModos(@PathParam("modo") String modo) {
		List<ProcedimentosRX> procedimentosRX = procedimentosRXService.filtarModoDoProcedimento(modo);
		return Response.ok(procedimentosRX).build();
	}

	@GET
	@Path("/modo2/{modo2}")
	public Response xlistarModos2(@PathParam("modo2") String modo2) {
		List<ProcedimentosRX> procedimentosRX = procedimentosRXService.filtarModo2DoProcedimento(modo2);
		return Response.ok(procedimentosRX).build();
	}

	@POST
	public Response salvarProcedimentosRX(ProcedimentosRXDto dto) {
		try {
			ProcedimentosRX procedimentosRX = procedimentosRXService.salvarProcedimentosRX(dto);
			return Response.ok(procedimentosRX).status(201).build();
		} catch (IllegalArgumentException ex) {
			return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
		}
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarProcedimentoRX(@PathParam("id") int id, ProcedimentosRXDto dto) {
		try {
			procedimentosRXService.atualizarProcedimentoRX(id, dto);
			return Response.status(204).build();
		} catch (IllegalArgumentException ex) {
			return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
		}
	}

	@DELETE
	@Path("/{id}")
	public Response apagarProcedimentoRX(@PathParam("id") int id) {
		procedimentosRXService.apagarProcedimentoRX(id);
		return Response.status(204).build();
	}

}
