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

import br.com.iv.dto.SetorCensoDto;
import br.com.iv.entity.SetorCenso;
import br.com.iv.service.SetorCensoService;

@Path("setorcenso")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SetorCensoController {

	@Inject
	SetorCensoService setorCensoService;

	@GET
	public Response listarSetor() {
		List<SetorCenso> setor = setorCensoService.listarTodosSetores();
		return Response.ok(setor).build();
	}

	@GET
	@Path("/titulo/{nome}/unidade/{unidade}")
	public Response xlistarSetor(@PathParam("nome") String nome, @PathParam("unidade") String unidade) {
		List<SetorCenso> setor = setorCensoService.filtarPorSetorCenso(nome, unidade);
		return Response.ok(setor).build(); 
	}

	@GET
	@Path("/codigo/{codigo}/unidade/{unidade}")
	public Response xlistarSetorCodigo(@PathParam("codigo") String codigo, @PathParam("unidade") String unidade) {
		List<SetorCenso> setor = setorCensoService.filtarPorSetorCensoCodigo(codigo, unidade);
		return Response.ok(setor).build();
	}

	@GET
	@Path("/id/{id}/excluido/{excluido}")
	public void alterarSenha(@PathParam("id") int id, @PathParam("excluido") boolean excluido) {
		setorCensoService.alterarSeto(id, excluido);
	}

	@PATCH
	@Path("/{id}/exclusao-logica/{excluido}")
	public Response atualizarExclusaoLogica(@PathParam("id") int id, @PathParam("excluido") boolean excluido) {
		setorCensoService.alterarSeto(id, excluido);
		return Response.status(204).build();
	}

	@GET
	@Path("/sigla/{sigla}/opcao/{opc}")
	public Response xlistarSligaUnidade(@PathParam("sigla") String sigla, @PathParam("opc") String opc) {
		SetorCensoDto dto = new SetorCensoDto();
		List<SetorCenso> setorCensoUnidade = setorCensoService.filtarPorSigladaUnidade(sigla,opc, dto);
		return Response.ok(setorCensoUnidade).build();
	}
	
	@GET
	@Path("/sigla2/{sigla}")
	public Response xlistarSligaUnidade2(@PathParam("sigla") String sigla) {
		List<SetorCenso> setorCensoUnidade = setorCensoService.filtarPorSigladaUnidade2(sigla);
		return Response.ok(setorCensoUnidade).build();
	}
	
	@GET
	@Path("/siglaobs/{sigla}/opcao/{opc}")
	public Response xlistarSligaUnidadeObs(@PathParam("sigla") String sigla, @PathParam("opc") String opc) {
		SetorCensoDto dto = new SetorCensoDto();
		List<SetorCenso> setorCensoUnidade = setorCensoService.filtarPorSigladaUnidadeObs(sigla,opc, dto);
		return Response.ok(setorCensoUnidade).build();
	}

	@GET
	@Path("/hospitalDistintos/{unidade}")
	public Response listarHospitaisDistintos(@PathParam("unidade") String unidade) {
		List<String> setorCensoUnidade = setorCensoService.filtrarPorSiglaUnidadeHospitalar(unidade);
		return Response.ok(setorCensoUnidade).build();
	}
	
	@GET
	@Path("/hospitalDistintosbylocal/{unidade}")
	public Response filtrarPorSiglaSetorHospitalar(@PathParam("unidade") String unidade) {
		List<String> setorCensoUnidade = setorCensoService.filtrarPorSiglaSetorHospitalar(unidade);
		return Response.ok(setorCensoUnidade).build();
	}
	

	@GET
	@Path("/unidadenir/{unidade}")
	public long contagem(@PathParam("unidade") String unidade)  {
		return setorCensoService.contagem(unidade); 
	}


	@POST
	public Response salvarSetorCenso(SetorCensoDto dto) {
		SetorCenso setorCenso = setorCensoService.salvarSetorCenso(dto);
		return Response.ok(setorCenso).status(201).build();
	}

	@PUT
	@Path("{id}")
	public Response atualizarSetorCenso(@PathParam("id") int id, SetorCensoDto dto) {
		setorCensoService.atualizarSetorCenso(id, dto);
		return Response.status(204).build(); 
	}

}
