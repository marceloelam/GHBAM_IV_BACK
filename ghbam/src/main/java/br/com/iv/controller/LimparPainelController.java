package br.com.iv.controller;

import br.com.iv.service.LimparService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("limparpainel")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LimparPainelController {

	@Inject
	LimparService limparService;
	
	@GET
	@Path("/unidade/{unidade}")
	public void limparPainel(@PathParam("unidade") String unidade) {
		 limparService.limparPainel(unidade);
	}

	@DELETE
	@Path("/unidade/{unidade}")
	public Response removerChamadasDoPainel(@PathParam("unidade") String unidade) {
		 limparService.limparPainel(unidade);
		 return Response.status(204).build();
	}
	
}
