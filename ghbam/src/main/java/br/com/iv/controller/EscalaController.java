package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.EscalaDto;
import br.com.iv.entity.Escala;
import br.com.iv.service.EscalaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
// ...existing code...
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("escalamedica")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EscalaController {

	@Inject
	EscalaService escalaService;

	@GET
	@Path("/escalamesanounidade/{mes}/{ano}/{yunidade}")
	public Response escalames(@PathParam("mes") int mes, @PathParam("ano") int ano,
			@PathParam("yunidade") String yunidade) {
		List<Escala> escalamedico = escalaService.escalamedica(mes, ano, yunidade);
		return Response.ok(escalamedico).build();
	}

	@GET
	@Path("/escalamesanomedico/{mes}/{ano}/{nome}/{yunidade}")
	public Response escalamesanomedico(@PathParam("mes") int mes, @PathParam("ano") int ano,
			@PathParam("nome") String nome, @PathParam("yunidade") String yunidade) {
		List<Escala> escalamedico = escalaService.escalamedicoindividual(mes, ano, nome, yunidade);
		return Response.ok(escalamedico).build();
	}
	
	@GET
	@Path("/escalaespecialidade/{especialidade}/{yunidade}")
	public Response escalaespecialidade(@PathParam("especialidade") String especialidade, @PathParam("yunidade") String yunidade) {
		List<Escala> escalamedico = escalaService.escalaEspecialidade(especialidade, yunidade);
		return Response.ok(escalamedico).build();
	}

	@POST
	public Response salvarEscala(EscalaDto dto) {
		Escala escalaUnidade = escalaService.salvarEscalaMedica(dto);
		return Response.ok(escalaUnidade).status(201).build();
	}

	@PUT
	@Path("/atualizarEscala/{id}")
	public Response atualizarEscala(@PathParam("id") int id, EscalaDto dto) {
		escalaService.atualizarEscala(id, dto);
		return Response.status(204).build();
	}

	@DELETE
	@Path("/removerEscala/{id}")
	public Response removerEscalar(@PathParam("id") Long id, EscalaDto dto) {
		escalaService.removeEscalar(id);
		return Response.status(204).build();
	}

	@DELETE
	@Path("/removerEscalaDiante/{id}/{data}")
	public void removerEscalarDiante(@PathParam("id") Long id, @PathParam("data") String data) {
		escalaService.removeEscalarDiante(id, data);

	}

}
