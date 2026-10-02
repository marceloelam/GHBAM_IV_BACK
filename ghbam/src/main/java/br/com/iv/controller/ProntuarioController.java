package br.com.iv.controller;

import br.com.iv.dto.ProntuarioDto;
import br.com.iv.entity.Prontuario;
import br.com.iv.service.ProntuarioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("prontuario")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProntuarioController {

	@Inject
	ProntuarioService prontuarioService;

	@POST
	public Response salvarProntuario(ProntuarioDto dto) {
		Prontuario prontuario = prontuarioService.salvarProntuario(dto);
		return Response.ok(prontuario).status(201).build(); 
	}
	
	
	       @GET
	       @Path("/cod/{cod}")
	       public Response listarPorControle(@PathParam("cod") String cod) {
		       Prontuario prontuario = prontuarioService.listarPorControle(cod);
		       if (prontuario == null) {
			       return Response.status(Response.Status.NOT_FOUND)
				       .entity("{\"erro\":\"Prontuário não encontrado\"}")
				       .build();
		       }
		       return Response.ok(prontuario).build();
	       }
	
	
}
