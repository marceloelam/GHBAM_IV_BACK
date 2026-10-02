package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.LocalCensoDto;
import br.com.iv.entity.LocalCenso;
import br.com.iv.service.LocalCensoService;

@Path("localcenso")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LocalCensoController {

	@Inject
	LocalCensoService localCensoService;

	@GET
	public Response listarSetor() {
		List<LocalCenso> setor = localCensoService.listarTodosSetores();
		return Response.ok(setor).build();
	}

	@GET
	@Path("/titulo/{nome}/unidade/{unidade}")
	public Response xlistarLocal(@PathParam("nome") String nome, @PathParam("unidade") String unidade) {
		List<LocalCenso> setor = localCensoService.filtarPorLocalCenso(nome, unidade);
		return Response.ok(setor).build();
	}

	@GET
	@Path("/codigo/{codigo}/unidade/{unidade}")
	public Response xlistarSetorCodigo(@PathParam("codigo") String codigo, @PathParam("unidade") String unidade) {
		List<LocalCenso> setor = localCensoService.filtarPorLocalCensoCodigo(codigo, unidade);
		return Response.ok(setor).build();
	}

	@GET
	@Path("/id/{id}/excluido/{excluido}")
	public void alterarSenha(@PathParam("id") int id, @PathParam("excluido") boolean excluido) {
		localCensoService.alterarSeto(id, excluido);
	}

	@PATCH
	@Path("/{id}/exclusao-logica/{excluido}")
	public Response atualizarExclusaoLogica(@PathParam("id") int id, @PathParam("excluido") boolean excluido) {
		localCensoService.alterarSeto(id, excluido);
		return Response.status(204).build();
	}

	@GET
	@Path("/sigla/{sigla}")
	public Response xlistarSligaUnidade(@PathParam("sigla") String sigla) {
		LocalCensoDto dto = new LocalCensoDto(0, null, null, false, false);
		List<LocalCenso> localCensoUnidade = localCensoService.filtarPorSigladaUnidade(sigla, dto);
		return Response.ok(localCensoUnidade).build();
	}

	@GET
	@Path("/hospitalDistintos/{unidade}")
	public Response listarHospitaisDistintos(@PathParam("unidade") String unidade) {
		List<String> localCensoUnidade = localCensoService.filtrarPorSiglaUnidadeHospitalar(unidade);
		return Response.ok(localCensoUnidade).build();
	}
	
	// Agregado por unidade (todos os setores) — usado por NirRestaurarInternacao
	// e CcihResumo, que nao selecionam um setor especifico.
	@GET
	@Path("/localunidade/{unidade}")
	public Response listarHospitaisDistintosLocalUnidade(@PathParam("unidade") String unidade) {
		List<LocalCensoDto> localCensoUnidade = localCensoService.filtrarPorSiglaUnidadeHospitalarLocalUnidade(unidade);
		return Response.ok(localCensoUnidade).build();
	}
		@GET
	@Path("/localunidade/{unidade}/pontoidsetor/{id}")
	public Response listarHospitaisDistintosLocalDoSetorCenso(@PathParam("unidade") String unidade, @PathParam("id") int id) {
		List<LocalCensoDto> localCensoUnidade = localCensoService.filtrarPorSiglaUnidadeHospitalarLocalSetorCenso(unidade, id);
		return Response.ok(localCensoUnidade).build();
	}
	
	@GET
	@Path("/localunidadeobservacao/{unidade}/pontoidsetor/{id}")
	public Response listarHospitaisDistintosLocalDoSetorCensoobservacao(@PathParam("unidade") String unidade, @PathParam("id") int id) {
		List<LocalCensoDto> localCensoUnidade = localCensoService.filtrarPorSiglaUnidadeHospitalarLocalSetorCensoObservacao(unidade, id);
		return Response.ok(localCensoUnidade).build();
	}

	@GET
	@Path("/unidadenir/{unidade}")
	public long contagem(@PathParam("unidade") String unidade)  {
		return localCensoService.contagem(unidade); 
	}


	@POST
	public Response salvarLocalCenso(LocalCensoDto dto) {
		LocalCenso localCenso = localCensoService.salvarLocalCenso(dto);
		return Response.ok(localCenso).status(201).build();
	}

	@PUT
	@Path("{id}")
	public Response atualizarLocalCenso(@PathParam("id") int id, LocalCensoDto dto) {
		localCensoService.atualizarLocalCenso(id, dto);
		return Response.status(204).build(); 
	}

}
