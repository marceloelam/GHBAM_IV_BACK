package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.PedidoResultadoLaboratorioDto;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;
import br.com.iv.service.PedidoResultadoLaboratorioService;
import br.com.iv.util.ExameRequest;

@Path("pedidoResultadoLaboratorio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PedidoResultadoLaboratorioController {

	@Inject
	PedidoResultadoLaboratorioService pedidoResultadoLaboratorioService;

	@GET
	@Path("/bam/{bam}")
	public Response xlistarPedidoResultadoLaboratorio(@PathParam("bam") int bam) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio = pedidoResultadoLaboratorioService
				.filtrarBamPedidoResultadoLaboratorio(bam);
		return Response.ok(pedidoResultadoLaboratorio).build();
	}

	@GET
	@Path("/idpaciente/{idpaciente}")
	public Response filtrarPeloIDPaciente(@PathParam("idpaciente") int idpaciente) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio = pedidoResultadoLaboratorioService
				.filtrarPeloIDPaciente(idpaciente);
		return Response.ok(pedidoResultadoLaboratorio).build();
	}

	@POST
	public Response salvarPedidoResultadoLaboratorio(PedidoResultadoLaboratorioDto dto) {
		PedidoResultadoLaboratorio pedidoResultadoLaboratorio = pedidoResultadoLaboratorioService
				.salvarPedidoResultadoLaboratorio(dto);
		return Response.ok(pedidoResultadoLaboratorio).status(201).build();
	}
	

	// Exames lidos do PDF do laudo (V189), para conferir/completar o LOINC.
	@GET
	@Path("/{idPedido}/itens")
	public Response listarItens(@PathParam("idPedido") int idPedido) {
		return Response.ok(PedidoResultadoLaboratorioItem.doResultado(idPedido)).build();
	}

	// Completa os dados RNDS de um resultado já salvo e o coloca (ou recoloca) na fila de envio.
	@PUT
	@Path("/{idPedido}/rnds")
	public Response completarDadosRnds(@PathParam("idPedido") int idPedido, PedidoResultadoLaboratorioDto dto) {
		PedidoResultadoLaboratorio resultado = pedidoResultadoLaboratorioService.completarDadosRnds(idPedido, dto);
		if (resultado == null) {
			return Response.status(Response.Status.NOT_FOUND).entity("Resultado laboratorial não encontrado.").build();
		}
		return Response.ok(resultado).build();
	}

	@POST
	@Path("/pdfExame/{idPedido}/liberou/{servidor}")
	public Response atualizarExame(@PathParam("idPedido") int idPedido, @PathParam("servidor") String servidor,
			ExameRequest exameRequest) {

		String arquivoPDF = exameRequest.getArquivoPDF();

		pedidoResultadoLaboratorioService.pdfExame(idPedido, arquivoPDF, servidor);
		return Response.ok().build();
	}
	
	@GET
	@Path("/bamresultadorealizado/{idbam}")
	public Response lista_de_resultados_de_exames_pedidomedico2_lab_result(@PathParam("idbam") int idbam) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio = pedidoResultadoLaboratorioService
				.lista_de_resultados_de_exames_pedidomedico2_lab_result(idbam);
		return Response.ok(pedidoResultadoLaboratorio).build();
	}

}
