package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.SalasDto;
import br.com.iv.entity.Salas;
import br.com.iv.service.SalasService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("sala")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SalaController {

	@Inject
	SalasService salasService;

	@GET
	public Response listarSala() {
		List<Salas> sala = salasService.listarTodasSalas();
		return Response.ok(sala).build();
	}

	@GET
	@Path("/unidade/{unidade}/local/{local}")
	public Response xlistarSala(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		SalasDto dto = new SalasDto();
		List<Salas> salas = salasService.filtarPorSalas(unidade, local, dto);
		return Response.ok(salas).build();
	}
	
	
	@GET
	@Path("/unidade/{unidade}/sala/{local}")
	public Response filtarPorSalaunidade(@PathParam("unidade") String unidade, @PathParam("local") String local) {
		SalasDto dto = new SalasDto();
		List<Salas> salas = salasService.filtarPorSalaunidade(unidade, local, dto);
		return Response.ok(salas).build();
	}
	
	

	@GET
	@Path("/unidade/{unidade}")
	public Response xlistarSala(@PathParam("unidade") String unidade) {
		SalasDto dto = new SalasDto();
		List<Salas> salas = salasService.filtarPorSalas(unidade, dto);
		return Response.ok(salas).build();
	}

	@POST
	public Response salvarSalas(SalasDto dto) {
		Salas salas = salasService.salvarSala(dto);
		return Response.ok(salas).status(201).build();
	}

	@PUT
	@Path("{id}")
	public Response atualizarSalas(@PathParam("id") int id, SalasDto dto) {
		salasService.atualizarSala(id, dto);
		return Response.status(204).build();
	}

	@DELETE
	@Path("{id}")
	public Response removerSalas(@PathParam("id") int id) {
		try {
			salasService.removeSala(id);
			return Response.status(204).build();
		} catch (jakarta.ws.rs.NotFoundException e) {
			return Response.status(404).entity(e.getMessage()).build();
		} catch (Exception e) {
			return Response.status(500).entity("Erro ao remover sala: " + e.getMessage()).build();
		}
	}

}
