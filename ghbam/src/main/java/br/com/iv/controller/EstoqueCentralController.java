package br.com.iv.controller;

import java.util.List;

import br.com.iv.entity.EstoqueCentral;
import br.com.iv.dto.EstoqueCentralDto;
import br.com.iv.dto.PacientesDto;
import br.com.iv.service.EstoqueCentralService;
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

@Path("estoquecentral")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EstoqueCentralController {

	@Inject
	EstoqueCentralService estoqueCentralService;
	

	@GET
	@Path("/unidade/{unidade}")
	public Response listarTudo(@PathParam("unidade") String unidade) {
		List<EstoqueCentral> lista = estoqueCentralService.listarTudo();
		return Response.ok(lista).build();
	}
	
	@GET
	@Path("/material/{material}/unidade/{unidade}")
	public Response listarEstoqueCentral(@PathParam("material") String material, @PathParam("unidade") String unidade) {
		List<EstoqueCentralDto> lista = estoqueCentralService.listarTodosEstoqueDto(material, unidade);
		return Response.ok(lista).build();
	}

	@GET
	@Path("/lote/{lote}/unidade/{unidade}")
	public Response listarEstoqueCentralLote(@PathParam("lote") String lote, @PathParam("unidade") String unidade) {
		List<EstoqueCentralDto> lista = estoqueCentralService.listarTodosEstoqu2eDto(lote, unidade);
		return Response.ok(lista).build();
	}
	
	
	
	@POST
	public Response criar(EstoqueCentralDto dto) {
		EstoqueCentral salvo = estoqueCentralService.salvar(dto);
		return Response.status(Response.Status.CREATED).entity(salvo).build();
	}


	
	@PUT
	@Path("{id}")
	public Response atualizar(@PathParam("id") int id, EstoqueCentralDto dto) {
		estoqueCentralService.atualizar(id, dto);
		return Response.status(204).build();
	}
	

	@DELETE
	@Path("/remover/{id}")
	public Response deletar(@PathParam("id") int id) {
		boolean removido = estoqueCentralService.deletar(id);
		if (!removido) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		return Response.noContent().build();
	}
}
