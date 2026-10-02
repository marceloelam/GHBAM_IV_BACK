package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.CboDto;
import br.com.iv.entity.Cbo;
import br.com.iv.service.CboService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("cbo")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CboController {

	@Inject
	CboService cboService;
	
	@GET
	public Response listarCbo() {
		List<Cbo> cbo = cboService.listarTodosCBO();
		return Response.ok(cbo).build();
	}
	
	@GET
	@Path("/titulo/{nome}")
	public Response xlistarCBO(@PathParam("nome") String ynome) {
		CboDto dto = new CboDto();
		List<Cbo> cbo = cboService.filtarPorCBO(ynome, dto);
		return Response.ok(cbo).build();
	}
	@GET
	@Path("/codigo/{codigo}")
	public Response xlistarCBOCodigo(@PathParam("codigo") String codigo) {
		CboDto dto = new CboDto();
		List<Cbo> cbo = cboService.filtarPorCBOCodigo(codigo, dto);
		return Response.ok(cbo).build();
	}
	
	
	}
