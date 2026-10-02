package br.com.iv.controller;

import java.util.List;

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


import br.com.iv.dto.ConfiguracaoUnidadeDto;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.service.ConfiguracaoUnidadeService;

@Path("configuracaoUnidade")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConfiguracaoUnidadeController {

	@Inject
	ConfiguracaoUnidadeService configuracaoUnidadeService;
	
	@GET
	public Response listarHospitais() {
		List<ConfiguracaoUnidade> configuracaoUnidade = configuracaoUnidadeService.listarTodosHospitais();
		return Response.ok(configuracaoUnidade).build();
	}
	
	@GET
	@Path("/hospitalDistintos")
	public Response listarHospitaisDistintos() {
		List<ConfiguracaoUnidade> configuracaoUnidade = configuracaoUnidadeService.filtarPorSiglaUnidadeHospitalar();
		return Response.ok(configuracaoUnidade).build();
	}

	@GET
	@Path("/sigla/{sigla}")
	public Response xlistarSligaUnidade(@PathParam("sigla") String sigla) {
		ConfiguracaoUnidadeDto dto = new ConfiguracaoUnidadeDto();
		List<ConfiguracaoUnidade> configuracaoUnidade = configuracaoUnidadeService.filtarPorSigladaUnidade(sigla, dto);
		return Response.ok(configuracaoUnidade).build();
	}

	
	@GET
	@Path("/hospital/{yunidade}")
	public Response xlistarUnidadeHospitalar(@PathParam("yunidade") String yunidade) {
		ConfiguracaoUnidadeDto dto = new ConfiguracaoUnidadeDto();
		List<ConfiguracaoUnidade> configuracaoUnidade = configuracaoUnidadeService.filtarPorUnidadeHospitalar(yunidade, dto);
		return Response.ok(configuracaoUnidade).build();
	}
	
	
	
	@POST
	public Response salvarSetores(ConfiguracaoUnidadeDto dto) {
		ConfiguracaoUnidade configuracaoUnidade = configuracaoUnidadeService.salvarUnidadeHospitalar(dto);
		return Response.ok(configuracaoUnidade).status(201).build();
	}
	
	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarSetor(@PathParam("id") int id, ConfiguracaoUnidadeDto dto) {
		configuracaoUnidadeService.atualizarSetor(id, dto);
		return Response.status(204).build();
	}

	@DELETE
	@Path("/remover/{id}")
	public Response removerSetor(@PathParam("id") Long id, ConfiguracaoUnidadeDto dto) {
		configuracaoUnidadeService.removeSetor(id);
		return Response.status(204).build();
	}
}
