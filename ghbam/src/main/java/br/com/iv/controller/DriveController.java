package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.DriveDto;
import br.com.iv.entity.Drive;
import br.com.iv.service.DriveService;
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

@Path("drive")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DriveController {

	@Inject
	DriveService driveService;

	@GET
	public Response listar() {
		List<Drive> drives = driveService.listarTodos();
		return Response.ok(drives).build();
	}

	@GET
	@Path("/unidade/{unidade}")
	public Response listarPorUnidade(@PathParam("unidade") String unidade) {
		List<Drive> drives = driveService.listarPorUnidade(unidade);
		return Response.ok(drives).build();
	}

	@GET
	@Path("/menu/{menu}/unidade/{unidade}")
	public Response listarPorMenuEUnidade(@PathParam("menu") String menu, @PathParam("unidade") String unidade) {
		List<Drive> drives = driveService.listarPorMenuEUnidade(menu, unidade);
		return Response.ok(drives).build();
	}

	@POST
	public Response salvar(DriveDto dto) {
		Drive drive = driveService.salvar(dto);
		return Response.ok(drive).status(201).build();
	}

	@PUT
	@Path("{id}")
	public Response atualizar(@PathParam("id") int id, DriveDto dto) {
		try {
			driveService.atualizar(id, dto);
			return Response.status(204).build();
		} catch (jakarta.ws.rs.NotFoundException e) {
			return Response.status(404).entity(e.getMessage()).build();
		}
	}

	@DELETE
	@Path("{id}")
	public Response remover(@PathParam("id") int id) {
		try {
			driveService.remover(id);
			return Response.status(204).build();
		} catch (jakarta.ws.rs.NotFoundException e) {
			return Response.status(404).entity(e.getMessage()).build();
		} catch (Exception e) {
			return Response.status(500).entity("Erro ao remover drive: " + e.getMessage()).build();
		}
	}
}
