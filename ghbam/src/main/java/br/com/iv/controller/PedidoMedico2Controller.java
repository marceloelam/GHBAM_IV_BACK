package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PedidoMedico2Dto;
import br.com.iv.dto.PedidoMedico2xDto;
import br.com.iv.dto.PedidoMedico2yDto;
import br.com.iv.entity.PedidoMedico2;
import br.com.iv.service.PedidoMedico2Service;
import br.com.iv.util.MensagemDeErro;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("pedidoMedico2")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PedidoMedico2Controller {

	@Inject
	PedidoMedico2Service pedidoMedico2Service;

	@GET
	@Path("/bam/{idbam}")
	public Response xlistarPedidos(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBam(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	// --------------------- internados emergencia coleta------------------------
	@GET
	@Path("/coletabaminternadosemergencia/{idbam}")
	public Response filtarBamColetarInternadosemergencia(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamColetarEmergencia(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/internadoemergenciaunidade/{unidade}/local/{local}")
	public Response xlistarColetaInternadoemergencia(@PathParam("unidade") String unidade,
			@PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.coletaEmergencia(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarcoletainternadosemergencia/{bam}")
	public void alterarColetaInternadosemergencia(@PathParam("bam") int bam) {
		alterarColetaInternadosEmergenciaPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/coleta-internados-emergencia")
	public void alterarColetaInternadosemergenciaPatch(@PathParam("bam") int bam) {
		alterarColetaInternadosEmergenciaPorBam(bam);
	}

	private void alterarColetaInternadosEmergenciaPorBam(int bam) {
		pedidoMedico2Service.alterarEmergencia(bam);
	}

	@GET
	@Path("/alterarcoletaemergencia/{bam}")
	public void alterarColetaEmergencia(@PathParam("bam") int bam) {
		alterarColetaEmergenciaPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/coleta-emergencia")
	public void alterarColetaEmergenciaPatch(@PathParam("bam") int bam) {
		alterarColetaEmergenciaPorBam(bam);
	}

	private void alterarColetaEmergenciaPorBam(int bam) {
		pedidoMedico2Service.alterarEmergencia(bam);
	}

	// --------------------- internados coleta------------------------
	@GET
	@Path("/coletabaminternados/{idbam}")
	public Response filtarBamColetarInternados(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamColetarInternados(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/internadounidade/{unidade}/local/{local}")
	public Response xlistarColetaInternado(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.coletaInternado(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarcoletainternados/{bam}")
	public void alterarColetaInternados(@PathParam("bam") int bam) {
		alterarColetaInternadosPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/coleta-internados")
	public void alterarColetaInternadosPatch(@PathParam("bam") int bam) {
		alterarColetaInternadosPorBam(bam);
	}

	private void alterarColetaInternadosPorBam(int bam) {
		pedidoMedico2Service.alterarInternado(bam);
	}

	@GET
	@Path("/alterarcoletainternado/{bam}")
	public void alterarColetaInternado(@PathParam("bam") int bam) {
		alterarColetaInternadoPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/coleta-internado")
	public void alterarColetaInternadoPatch(@PathParam("bam") int bam) {
		alterarColetaInternadoPorBam(bam);
	}

	private void alterarColetaInternadoPorBam(int bam) {
		pedidoMedico2Service.alterarInternado(bam);
	}
//---------------------------- coleta ---------------------------

	@GET
	@Path("/coletabamps/{idbam}")
	public Response filtarBamColetarPs(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamColetarPs(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/psunidade/{unidade}/local/{local}")
	public Response xlistarColetaPs(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.coletaPs(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarcoletaps/{bam}")
	public void alterarColetaPs(@PathParam("bam") int bam) {
		alterarColetaPsPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/coleta-ps")
	public void alterarColetaPsPatch(@PathParam("bam") int bam) {
		alterarColetaPsPorBam(bam);
	}

	private void alterarColetaPsPorBam(int bam) {
		pedidoMedico2Service.alterarPs(bam);
	}

	// --------------------------- fim coleta -----------------------------------

	// --------------------- internados emergencia
	// laboratório------------------------
	@GET
	@Path("/labbaminternadosemergencia/{idbam}")
	public Response filtarBamLabInternadosemergencia(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamLabEmergencia(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/labinternadoemergenciaunidade/{unidade}/local/{local}")
	public Response xlistarLabInternadoemergencia(@PathParam("unidade") String unidade,
			@PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.labEmergencia(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarlabinternadosemergencia/{bam}")
	public void alterarLabInternadosemergencia(@PathParam("bam") int bam) {
		alterarLabInternadosEmergenciaPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/laboratorio-internados-emergencia")
	public void alterarLabInternadosemergenciaPatch(@PathParam("bam") int bam) {
		alterarLabInternadosEmergenciaPorBam(bam);
	}

	private void alterarLabInternadosEmergenciaPorBam(int bam) {
		pedidoMedico2Service.alterarEmergencia(bam);
	}

	@GET
	@Path("/alterarlabemergencia/{bam}")
	public void alterarLabEmergencia(@PathParam("bam") int bam) {
		alterarLabEmergenciaPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/laboratorio-emergencia")
	public void alterarLabEmergenciaPatch(@PathParam("bam") int bam) {
		alterarLabEmergenciaPorBam(bam);
	}

	private void alterarLabEmergenciaPorBam(int bam) {
		pedidoMedico2Service.alterarLabEmergencia(bam);
	}

	// --------------------- internados lab------------------------
	@GET
	@Path("/labbaminternados/{idbam}")
	public Response filtarBamLabInternados(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamLabInternados(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/labinternadounidade/{unidade}/local/{local}")
	public Response xlistarLabInternado(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.labInternado(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarlabinternadoslab/{bam}")
	public void alterarLabInternados(@PathParam("bam") int bam) {
		alterarLabInternadosPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/laboratorio-internados")
	public void alterarLabInternadosPatch(@PathParam("bam") int bam) {
		alterarLabInternadosPorBam(bam);
	}

	private void alterarLabInternadosPorBam(int bam) {
		pedidoMedico2Service.alterarLabInternado(bam);
	}

	@GET
	@Path("/alterarcoletainternadolab/{bam}")
	public void alterarLabInternado(@PathParam("bam") int bam) {
		alterarColetaInternadoLabPorBam(bam);
	}

	@PATCH
	@Path("/{bam}/coleta-internado-lab")
	public void alterarLabInternadoPatch(@PathParam("bam") int bam) {
		alterarColetaInternadoLabPorBam(bam);
	}

	private void alterarColetaInternadoLabPorBam(int bam) {
		pedidoMedico2Service.alterarInternado(bam);
	}
//---------------------------- lab ---------------------------

	@GET
	@Path("/labbamlabps/{idbam}")
	public Response filtarBamLabPs(@PathParam("idbam") int idbam) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamLabPs(idbam);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/psunidadelab/{unidade}/local/{local}")
	public Response xlistarLabPs(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.labPs(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarlabps/{bam}/usuario/{user}")
	public void alterarLabPs(@PathParam("bam") int bam, @PathParam("user") String user) {
		alterarLabPsPorBam(bam, user);
	}

	@PATCH
	@Path("/{bam}/laboratorio-ps/usuario/{user}")
	public void alterarLabPsPatch(@PathParam("bam") int bam, @PathParam("user") String user) {
		alterarLabPsPorBam(bam, user);
	}

	private void alterarLabPsPorBam(int bam, String user) {
		pedidoMedico2Service.alterarLabPs(bam, user);
	}

	// --------------------------- fim laboratório
	// -----------------------------------

	@POST
	public Response salvarPedidoMedico2(PedidoMedico2Dto dto) {
		PedidoMedico2 pedidoMedico2 = pedidoMedico2Service.salvarPedidoMedico2(dto);
		return Response.ok(pedidoMedico2).status(201).build();
	}

	@DELETE
	@Path("{id}")
	public Response remover(@PathParam("id") int id, PedidoMedico2Dto dto) {
		try {
			pedidoMedico2Service.remove(id, dto);
			return Response.status(Response.Status.NO_CONTENT).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
		}
	}

	@GET
	@Path("/psunidadepedido2/{unidade}/local/{local}")
	public Response xlistarpedidoPs(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.pedido2Ps(unidade, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/examesbamps/{idbam}/local/{local}")
	public Response filtarBamExamesrPs(@PathParam("idbam") int idbam, @PathParam("local") String local) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtarBamExamesPs(idbam, local);
		return Response.ok(pedidoMedico2).build();
	}

	@GET
	@Path("/alterarRespostaExames/{bam}/resposta/{resposta}/local/{local}")
	public void atualizarAoMedicoRespostaExames(@PathParam("bam") int bam, @PathParam("resposta") String resposta,
			@PathParam("local") String local) {
		atualizarRespostaExamesPorBam(bam, resposta, local);
	}

	@PATCH
	@Path("/{bam}/resposta-exames/resposta/{resposta}/local/{local}")
	public void atualizarAoMedicoRespostaExamesPatch(@PathParam("bam") int bam, @PathParam("resposta") String resposta,
			@PathParam("local") String local) {
		atualizarRespostaExamesPorBam(bam, resposta, local);
	}

	private void atualizarRespostaExamesPorBam(int bam, String resposta, String local) {
		pedidoMedico2Service.atualizarAoMedicoRespostaExames(bam, resposta, local);
	}

	@GET
	@Path("/bamresultado/{bam}/qual/{qual}")
	public Response filtrarBamPedidoMedico2(@PathParam("bam") int bam, @PathParam("qual") String qual) {
		List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.filtrarBamPedidoMedico2(bam, qual);
		return Response.ok(pedidoMedico2).build();
	}

	/*
	 * @GET
	 * 
	 * @Path("/pedidosagrupamento/{bam}/obs/{obs}/local/{local}") public Response
	 * xlistarpedido(@PathParam("bam") int bam, @PathParam("obs") String
	 * obs, @PathParam("local") String local) { List<PedidoMedico2> pedidoMedico2 =
	 * pedidoMedico2Service.pedido2lista(bam, obs, local); return
	 * Response.ok(pedidoMedico2).build(); }
	 * 
	 */
	

	
	    @POST
	    @Path("/pedidosagrupamento")
	    public Response xlistarpedido(PedidoMedico2yDto dto) {
	    	String a = "1";
	    	a = "2";
	        try {
	            List<PedidoMedico2> pedidoMedico2 = pedidoMedico2Service.pedido2lista(dto);
	            return Response.status(Response.Status.CREATED).entity(pedidoMedico2).build();
	        } catch (MensagemDeErro e) {
	            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
	        } catch (Exception e) {
	            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Erro interno do servidor: " + e.getMessage()).build();
	        }
	    }
	
//======================================================================================
@GET
@Path("/psunidadepedido/{unidade}/local/{local}")
public Response xlistarpedidoMedico2Ps(@PathParam("unidade") String unidade, @PathParam("local") String local) {
	List<PedidoMedico2xDto> pedidoMedico2 = pedidoMedico2Service.pedido2Ps(unidade, local);
	return Response.ok(pedidoMedico2).build();
}

@GET
@Path("/diretosetor/{unidade}/local/{local}")
public Response xlistarPacientesDiretoSetor(@PathParam("unidade") String unidade, @PathParam("local") String local) {
	List<PedidoMedico2xDto> pacientes = pedidoMedico2Service.pacientesDiretoSetor(unidade, local);
	return Response.ok(pacientes).build();
}

@POST
@Path("/diretosetor/finalizar/{idbam}")
public Response xfinalizarExameDiretoSetor(@PathParam("idbam") int idbam) {
	pedidoMedico2Service.finalizarExameDiretoSetor(idbam);
	return Response.ok().build();
}


}
