package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.ProcedimentoSusValorEfetivoDto;
import br.com.iv.dto.ProcedimentoSusValorUnidadeDto;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.service.ProcedimentosSusService;

@Path("procedimentossus")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProcedimentosSusController {

	@Inject
	ProcedimentosSusService procedimentosSusService;


	@GET
    @Path("/procedimentosus/{procedimentosus}")
	public Response pegarProcedimento(@PathParam("procedimentosus") String procedimentosus) {
	    Object result = procedimentosSusService.ProcedimentoSusId(procedimentosus);

	    if (result == null) {
	        return Response.status(Response.Status.NOT_FOUND)
	                .entity("Procedimento não encontrado nem no SUS nem no RX")
	                .build();
	    }

	    return Response.ok(result).build();
	}

	@GET
    @Path("/procedimentosus/{procedimentosus}/descricao/{descricao}")
	public Response pegarProcedimentoComValorDaUnidade(@PathParam("procedimentosus") String procedimentosus,
			@PathParam("descricao") String descricao) {
	    ProcedimentoSusValorEfetivoDto result = procedimentosSusService.buscarValorEfetivo(procedimentosus, descricao);

	    if (result == null) {
	        return Response.status(Response.Status.NOT_FOUND)
	                .entity("Procedimento nÃ£o encontrado no SUS")
	                .build();
	    }

	    return Response.ok(result).build();
	}

	@GET
    @Path("/procedimentosus/{procedimentosus}/unidade/{unidade}")
	public Response pegarProcedimentoComValorDaUnidadeAtual(@PathParam("procedimentosus") String procedimentosus,
			@PathParam("unidade") String unidade) {
	    ProcedimentoSusValorEfetivoDto result = procedimentosSusService.buscarValorEfetivo(procedimentosus, unidade);

	    if (result == null) {
	        return Response.status(Response.Status.NOT_FOUND)
	                .entity("Procedimento nao encontrado no SUS")
	                .build();
	    }

	    return Response.ok(result).build();
	}

	@GET
	@Path("/valores")
	public Response pesquisarValores(@QueryParam("q") String termo,
			@QueryParam("unidade") String unidade,
			@QueryParam("limit") Integer limite) {
		int limiteBusca = limite == null ? 50 : limite;
		List<ProcedimentoSusValorEfetivoDto> procedimentosSus = procedimentosSusService.pesquisarValores(termo, unidade, limiteBusca);
		return Response.ok(procedimentosSus).build();
	}

	@POST
	@Path("/valores/unidade")
	public Response salvarValorUnidade(ProcedimentoSusValorUnidadeDto dto) {
		try {
			ProcedimentoSusValorEfetivoDto salvo = procedimentosSusService.salvarValorUnidade(dto);
			return Response.ok(salvo).build();
		} catch (IllegalArgumentException ex) {
			return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
		}
	}

	@GET
	@Path("/procedi/{texto}/cid/{cid}/cbo/{cbo}")
	public Response procediment(@PathParam("texto") String texto, @PathParam("cid") String cid,@PathParam("cbo") String cbo) {
		List<ProcedimentosSus> procedimentosSus = procedimentosSusService.procediment( texto,  cid,  cbo);
		return Response.ok(procedimentosSus).build(); 
	}


	@GET
	@Path("/proced2/{texto}")
	public Response procediment(@PathParam("texto") String texto) {
		List<ProcedimentosSus> procedimentosSus = procedimentosSusService.procediment2( texto);
		return Response.ok(procedimentosSus).build(); 
	}


	/** CIDs aceitos pelo SIGTAP para o procedimento ja escolhido. */
	@GET
	@Path("/procedi/{codigo}/cids")
	public Response cidsDoProcedimento(@PathParam("codigo") String codigo) {
		return Response.ok(procedimentosSusService.cidsDoProcedimento(codigo)).build();
	}
}
