package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

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
	
	@Inject
	br.com.iv.plataforma.municipio.MunicipioRoteamento municipios;

	// Separacao ligada: unidades de municipio com banco proprio continuam cadastradas no banco
	// principal (registro da plataforma), mas so o superusuario as ve aqui.
	@Inject
	jakarta.enterprise.inject.Instance<org.eclipse.microprofile.jwt.JsonWebToken> jwt;

	/** Matricula e superusuario pelo JWT validado (nesta rota o RBAC nao preenche "ghbam.user"). */
	private String[] sessao() {
		try {
			org.eclipse.microprofile.jwt.JsonWebToken t = br.com.iv.plataforma.municipio.MunicipioRoteamento.tokenDaRequisicao();
			if (t == null || t.getRawToken() == null) {
				return null;
			}
			Object m = t.getClaim("matricula");
			boolean superusuario = t.getGroups() != null
					&& (t.getGroups().contains("ksuperusuario") || t.getGroups().contains("superusuario"));
			return new String[] { m == null ? "" : m.toString().replace("\"", "").trim(), String.valueOf(superusuario) };
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Separacao ligada: cada municipio ve as proprias unidades. O responsavel da plataforma e quem ele
	 * designou veem tambem as unidades dos municipios em que ja foram ativados (para trocar de municipio
	 * pela "Unidade ativa"); os demais nao veem unidade de outro municipio. A lista pode vir como sigla
	 * (hospitalDistintos devolve texto) ou como cadastro completo.
	 */
	private List<Object> semOutrosMunicipios(List<?> lista) {
		List<Object> entrada = new java.util.ArrayList<>(lista);
		if (municipios == null || !municipios.ativa()) {
			return entrada;
		}
		String atual = municipios.atual();
		String[] s = sessao();
		boolean plataforma = s != null && Boolean.parseBoolean(s[1]) && municipios.podeAcessarMunicipios(s[0]);
		// Lista de siglas da "Unidade ativa" para quem circula entre municipios: so a unidade principal
		// dele em cada municipio onde foi ativado (decisao Marcelo 17/11).
		if (plataforma && (entrada.isEmpty() || !(entrada.get(0) instanceof ConfiguracaoUnidade))) {
			List<Object> principais = new java.util.ArrayList<>();
			for (String cod : municipios.municipios()) {
				String principal = municipios.unidadePrincipal(cod, s[0]);
				if (principal != null && !principais.contains(principal)) {
					principais.add(principal);
				}
			}
			if (!principais.isEmpty()) {
				return principais;
			}
		}
		java.util.Map<String, Boolean> acesso = new java.util.HashMap<>();
		java.util.function.Predicate<String> visivel = cod -> cod == null || cod.equals(atual)
				|| (!municipios.ehPadrao(cod) && !municipios.temBancoProprio(cod))
				|| (plataforma && acesso.computeIfAbsent(cod, c -> municipios.temAcesso(c, s[0])));
		List<Object> saida = new java.util.ArrayList<>();
		java.util.Set<String> siglas = new java.util.HashSet<>();
		for (Object o : entrada) {
			String sigla = o instanceof ConfiguracaoUnidade u ? u.getSigla() : String.valueOf(o);
			String cod = o instanceof ConfiguracaoUnidade u ? String.valueOf(u.getCodIbge())
					: municipios.municipioDaUnidade(sigla);
			if (visivel.test(cod)) {
				saida.add(o);
				siglas.add(sigla == null ? "" : sigla.trim().toUpperCase());
			}
		}
		// Dentro de outro municipio o banco so tem as unidades dele: acrescenta as dos demais (so siglas).
		if (plataforma && !entrada.isEmpty() && !(entrada.get(0) instanceof ConfiguracaoUnidade)) {
			for (String[] u : municipios.unidadesDaPlataforma()) {
				if (!siglas.contains(u[0].toUpperCase()) && visivel.test(u[1])) {
					saida.add(u[0]);
					siglas.add(u[0].toUpperCase());
				}
			}
		}
		return saida;
	}

	@GET
	public Response listarHospitais() {
		List<ConfiguracaoUnidade> configuracaoUnidade = configuracaoUnidadeService.listarTodosHospitais();
		return Response.ok(semOutrosMunicipios(configuracaoUnidade)).build();
	}

	@GET
	@Path("/hospitalDistintos")
	public Response listarHospitaisDistintos() {
		List<ConfiguracaoUnidade> configuracaoUnidade = configuracaoUnidadeService.filtarPorSiglaUnidadeHospitalar();
		return Response.ok(semOutrosMunicipios(configuracaoUnidade)).build();
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
	
	
	
	// Criar/alterar/remover unidade: so o superusuario (administrador da plataforma). A leitura
	// (GET) continua aberta porque todas as telas leem a configuracao da unidade.
	@Context
	ContainerRequestContext requestContext;

	private Response semSuperusuario() {
		if (requestContext != null && requestContext.getProperty("ghbam.user") instanceof AuthenticatedUser user
				&& (user.hasPermission("ksuperusuario") || user.hasPermission("superusuario"))) {
			return null;
		}
		return Response.status(Response.Status.FORBIDDEN)
				.entity(Map.of("mensagem", "Somente o superusuário pode cadastrar ou alterar unidades."))
				.build();
	}

	@POST
	public Response salvarSetores(ConfiguracaoUnidadeDto dto) {
		Response negado = semSuperusuario();
		if (negado != null) {
			return negado;
		}
		ConfiguracaoUnidade configuracaoUnidade = configuracaoUnidadeService.salvarUnidadeHospitalar(dto);
		return Response.ok(configuracaoUnidade).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarSetor(@PathParam("id") int id, ConfiguracaoUnidadeDto dto) {
		Response negado = semSuperusuario();
		if (negado != null) {
			return negado;
		}
		configuracaoUnidadeService.atualizarSetor(id, dto);
		return Response.status(204).build();
	}

	@DELETE
	@Path("/remover/{id}")
	public Response removerSetor(@PathParam("id") Long id, ConfiguracaoUnidadeDto dto) {
		Response negado = semSuperusuario();
		if (negado != null) {
			return negado;
		}
		configuracaoUnidadeService.removeSetor(id);
		return Response.status(204).build();
	}
}
