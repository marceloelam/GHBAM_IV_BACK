package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
// ...existing code...
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.CboDto;
import br.com.iv.dto.Cid1Dto;
import br.com.iv.entity.Cid1;
import br.com.iv.service.CidService;

@Path("cid")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CidController {

	@Inject
	CidService cidService;
	
	@GET
	public Response listarCid() {
		List<Cid1> cid = cidService.listarTodosCid();
		return Response.ok(cid).build();
	}
	
	@GET
	@Path("/titulo/{nome}")
	public Response xlistarCBO(@PathParam("nome") String ynome) {
		Cid1Dto dto = new Cid1Dto();
		List<Cid1> cid = cidService.filtarPorCid(ynome, dto);
		return Response.ok(cid).build();
	}
	@GET
	@Path("/codigo/{codigo}")
	public Response xlistarCidCodigo(@PathParam("codigo") String codigo) {
		CboDto dto = new CboDto();
		List<Cid1> cid = cidService.filtarPorCidCodigo(codigo, dto);
		return Response.ok(cid).build();
	}
    
	@GET
	@Path("/cidnome/{nome}") 
	public Response xlistarCid(@PathParam("nome") String nome) {
		CboDto dto = new CboDto();
		List<Cid1> cid = cidService.filtarPorCid(nome, dto);
		return Response.ok(cid).build();
	}
	
	
	
	}
