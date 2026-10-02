package br.com.iv.controller;

import java.util.List;

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

import jakarta.ws.rs.DELETE;

import br.com.iv.dto.SetoresDto;
import br.com.iv.dto.SetoresUnidadesDto;
import br.com.iv.entity.Setores;
import br.com.iv.service.SetoresService;

@Path("setores")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SetoresController {

	@Inject
	SetoresService setoresService;
	
	@GET
	public Response listarSetores() {
		List<Setores> setores = setoresService.listarTodosOsSetores();
		return Response.ok(setores).build();
	}
	
	@GET
	@Path("/todossetores")
	public Response listarTodosOsSetoresUnidades() {
		List<SetoresUnidadesDto> setores = setoresService.listarTodosOsSetoresUnidades();
		return Response.ok(setores).build();
	}

	@GET
	@Path("/setor/{ysetor}/unidade/{yunidade}")
	public Response xlistarSetores(@PathParam("ysetor") String ysetor, @PathParam("yunidade") String yunidade) {
		SetoresDto dto = new SetoresDto();
		List<Setores> setores = setoresService.filtarPorSetorEUnidade(ysetor, yunidade, dto);
		return Response.ok(setores).build();
	}
	
	@GET
	@Path("/unidade/{yunidade}")
	public Response xlistarUnidades(@PathParam("yunidade") String yunidade) {
		SetoresDto dto = new SetoresDto();
		List<Setores> setores = setoresService.filtarPorUnidadeDoSetor(yunidade, dto);
		return Response.ok(setores).build();
	}
	
	@GET
	@Path("/unidadetriar/{yunidade}")
	public Response xlistarUnidadestriar(@PathParam("yunidade") String yunidade) {
		SetoresDto dto = new SetoresDto();
		List<Setores> setores = setoresService.filtarPorUnidadeDoSetorTriar(yunidade,dto);
		return Response.ok(setores).build();
	}
	
	@GET
	@Path("/unidadesdistintas")
	public Response xlistarUnidades() {
		SetoresDto dto = new SetoresDto();
		List<Setores> setores = setoresService.filtarUnidadesDistintasDoSetor(dto);
		return Response.ok(setores).build();
	}
	
	@POST
	public Response salvarSetores(SetoresDto dto) {
		Setores setores = setoresService.salvarSetores(dto);
		return Response.ok(setores).status(201).build();
	}
	
	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarSetor(@PathParam("id") int id, SetoresDto dto) {
		setoresService.atualizarSetor(id, dto);
		return Response.status(204).build();
	}
	
	@PUT
	@Path("/atualizarsigla/{siglaAntiga}/{siglaNova}")
	public Response atualizarSetorSigla(@PathParam("siglaAntiga") String siglaAntiga, @PathParam("siglaNova") String siglaNova, SetoresDto dto) {
		setoresService.atualizarSetorSigla(siglaAntiga, siglaNova, dto);
		return Response.status(204).build();
	}

	@DELETE
	@Path("/remover/{id}")
	public Response removerSetor(@PathParam("id") int id, SetoresDto dto) {
		setoresService.removeSetor(id);
		return Response.status(204).build();
	}
}
