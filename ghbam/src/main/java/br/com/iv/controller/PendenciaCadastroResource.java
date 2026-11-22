package br.com.iv.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.InatividadeAcessoService;
import br.com.iv.service.PendenciaCadastroService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Alertas de conta do servidor: cadastro profissional pendente (V264) e inatividade (V265).
 * Qualquer usuário logado lê a própria pendência; as listas da equipe e as ações saem
 * filtradas nos serviços (super: todas; adm: unidade; coordenador: setores que coordena).
 */
@Path("/pendencia-cadastro")
@Produces(MediaType.APPLICATION_JSON)
public class PendenciaCadastroResource {

	@Inject
	PendenciaCadastroService service;

	@Inject
	InatividadeAcessoService inatividade;

	public static class AfastamentoDto {
		public String ate;
		public String motivo;
	}

	private static AuthenticatedUser logado(ContainerRequestContext ctx) {
		return (AuthenticatedUser) ctx.getProperty("ghbam.user");
	}

	@GET
	public Response alertas(@Context ContainerRequestContext requestContext) {
		AuthenticatedUser logado = logado(requestContext);
		if (logado == null) {
			return Response.status(Response.Status.UNAUTHORIZED).build();
		}
		Map<String, Object> corpo = new LinkedHashMap<>();
		corpo.put("minha", service.minha(logado));
		corpo.put("equipe", service.equipe(logado));
		corpo.put("inativos", inatividade.inativos(logado));
		return Response.ok(corpo).build();
	}

	@POST
	@Path("/reativar/{idservidor}")
	public Response reativar(@PathParam("idservidor") int idservidor, @Context ContainerRequestContext requestContext) {
		try {
			inatividade.reativar(logado(requestContext), idservidor);
			return Response.noContent().build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", e.getMessage())).build();
		}
	}

	@POST
	@Path("/afastar/{idservidor}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response afastar(@PathParam("idservidor") int idservidor, AfastamentoDto dto,
			@Context ContainerRequestContext requestContext) {
		try {
			LocalDate ate = dto == null || dto.ate == null || dto.ate.isBlank() ? null : LocalDate.parse(dto.ate.trim());
			inatividade.afastar(logado(requestContext), idservidor, ate, dto == null ? null : dto.motivo);
			return Response.noContent().build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", e.getMessage())).build();
		} catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Informe a data de retorno (de hoje até 1 ano).")).build();
		}
	}
}
