package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.ChamadaDto;
import br.com.iv.entity.ChamadaPaciente;
import br.com.iv.service.ChamadaService;
import br.com.iv.service.PainelVozService;
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

@Path("chamada")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChamadaController {

	@Inject
	ChamadaService chamadaService;

	@Inject
	PainelVozService painelVozService;

	@GET
	@Path("/unidade1/{unidade}/unico/{tv}")
	public Response listaChamada1(@PathParam("unidade") String unidade, @PathParam("tv") String tv) {
		try {
			List<ChamadaPaciente> chamada1 = chamadaService.listarultimo(unidade, tv);
			return Response.ok(chamada1).build();
		} catch (Exception e) {
			return null;	
		}
		
		
	}

	@GET
	@Path("/unidade2/{unidade}/direita/{tv}")
	
	public Response filtarultimoDireita(@PathParam("unidade") String unidade, @PathParam("tv") String tv) {
		try {
			List<ChamadaPaciente> chamada1 = chamadaService.filtarultimoDireita(unidade, tv);
			return Response.ok(chamada1).build();
		} catch (Exception e) {
			// TODO: handle exception
		}
		return null;
	}
	
	
	@GET
	@Path("/unidade3/{unidade}/tv/{tv}")
	public Response listaChamada6(@PathParam("unidade") String unidade, @PathParam("tv") String tv) {
		
			List<ChamadaPaciente> chamada6 = chamadaService.listarseisultimos(unidade, tv);
			return Response.ok(chamada6).build();
		
	}
	
	@POST
	public Response salvarChamada(ChamadaDto dto) {
		ChamadaPaciente chamada = chamadaService.salvarChamada(dto);
		// Começa a gerar a voz em segundo plano; nunca lança exceção nem atrasa a resposta.
		painelVozService.preaquecer(chamada);
		return Response.ok(chamada).status(201).build();
	}


	@GET
	@Path("/remover/{unidade}/tv/{tv}") 
	public void removerChamadaUltima(@PathParam("unidade") String unidade, @PathParam("tv") String tv) {
		removerUltimaChamada(unidade, tv);
	}

	@DELETE
	@Path("/unidade/{unidade}/tv/{tv}") 
	public void removerChamadaUltimaDelete(@PathParam("unidade") String unidade, @PathParam("tv") String tv) {
		removerUltimaChamada(unidade, tv);
	}

	private void removerUltimaChamada(String unidade, String tv) {
		chamadaService.removeChamada(unidade, tv);
	}
	
	@GET
	@Path("/idchamada/{idchamada}/tv/{tv}") 
	public void atualizarChamada(@PathParam("idchamada") int idchamada, @PathParam("tv") String tv) {
		atualizarChamadaPorTv(idchamada, tv);
	}

	@PATCH
	@Path("/{idchamada}/tv/{tv}") 
	public void atualizarChamadaPatch(@PathParam("idchamada") int idchamada, @PathParam("tv") String tv) {
		atualizarChamadaPorTv(idchamada, tv);
	}

	private void atualizarChamadaPorTv(int idchamada, String tv) {
		chamadaService.atualizarChamada(idchamada, tv);
	}

	

}
