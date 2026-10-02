package br.com.iv.controller;

import java.util.List;

import br.com.iv.entity.Bulario;
import br.com.iv.service.BularioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("bulario")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BularioController {

	@Inject
	BularioService bularioService;
	
	
	@GET
	@Path("/busca/{nome}")
	public Response xlistarCBO(@PathParam("nome") String ynome) {
		List<Bulario> bula = bularioService.filtarBulario(ynome);
		return Response.ok(bula).build();
	}
	
	
	}
