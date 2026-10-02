package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.EscalaDeEnfermagemDto;
import br.com.iv.dto.ServidorDto;
import br.com.iv.entity.EscalaDeEnfermagem;
import br.com.iv.entity.Servidor;
import br.com.iv.service.EscalaDeEnfermagemService;
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

@Path("escaladeenfermagem")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EscalaDeEnfermagemController {

	@Inject
	EscalaDeEnfermagemService escaladeenfermagemservice;

	@GET
	@Path("/bam/{bam}/data/{data}/unidade/{unidade}")
	public Response xlistarEscala(@PathParam("bam") int bam, @PathParam("data") String data,
			@PathParam("unidade") String unidade) {
		List<EscalaDeEnfermagem> enfermagem = escaladeenfermagemservice.filtarescalaenfermagemPorBameData(bam, data,
				unidade);
		return Response.ok(enfermagem).build();
	}

	
	@POST
	public Response salvarEnfermagem(EscalaDeEnfermagemDto dto) {
		EscalaDeEnfermagem escala = escaladeenfermagemservice.salvarescalaenfermagem(dto);
		return Response.ok(escala).status(201).build();
	}
	
	
	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarEnfermagem(@PathParam("id") int id, EscalaDeEnfermagemDto dto) {
		escaladeenfermagemservice.atualizarescalaenfermagem(id, dto);
		return Response.status(204).build();
	}

}
