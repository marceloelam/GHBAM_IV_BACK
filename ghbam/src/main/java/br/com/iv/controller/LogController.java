package br.com.iv.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

import br.com.iv.dto.LogDto;
import br.com.iv.entity.LocalCenso;
import br.com.iv.entity.Log;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.LogService;

@Path("log")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LogController {
	private static final DateTimeFormatter HORA_LOG = DateTimeFormatter.ofPattern("HH:mm");
	private static final ZoneId ZONA_LOG = ZoneId.of("America/Sao_Paulo");

	@Inject
	LogService logService;
	
	@GET
	public Response listarLog() {
		List<Log> cbo = logService.listarTodosLogs();
		return Response.ok(cbo).build();
	}
	
	@GET
	@Path("/data/{data}/unidade/{unidade}")
	public Response xlistarlogData(@PathParam("data") String data, @PathParam("unidade") String unidade) {
		try {
			LocalDate dataFiltro = LocalDate.parse(data);
			List<Log> cbo = logService.filtarPorLogData(dataFiltro, unidade);
			return Response.ok(cbo).build();
		} catch (DateTimeParseException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(java.util.Map.of("erro", "Informe a data no formato AAAA-MM-DD."))
					.build();
		}
	}
	
	@GET
	@Path("/pesquisa/{pesquisa}/unidade/{unidade}")
	public Response xlistarLog(@PathParam("pesquisa") String pesquisa, @PathParam("unidade") String unidade) {
		List<Log> cbo =  logService.filtarPorLog(pesquisa, unidade);
		return Response.ok(cbo).build();
	}

	@POST
	public Response salvarLog(LogDto dto, @Context ContainerRequestContext requestContext) {
		if (dto == null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{\"erro\":\"Corpo da requisição é obrigatório\"}").build();
		}
		if (isHeartbeat(dto.getEvento())) return Response.noContent().build();
		enriquecerAutoria(dto, requestContext);
		Log log = logService.salvarLog(dto);
		return Response.ok(log).status(201).build();
	}

	@POST
	@jakarta.ws.rs.Path("async")
	public Response salvarLogAsync(LogDto dto, @Context ContainerRequestContext requestContext) {
		if (dto == null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{\"erro\":\"Corpo da requisição é obrigatório\"}").build();
		}
		if (isHeartbeat(dto.getEvento())) return Response.noContent().build();
		enriquecerAutoria(dto, requestContext);
		Log log = logService.salvarLogAsync(dto);
		return Response.accepted(log).build();
	}

	private boolean isHeartbeat(String evento) {
		String texto = String.valueOf(evento == null ? "" : evento).toLowerCase();
		return texto.contains("heartbeat") || texto.contains("heart-beat")
				|| texto.contains("usuarios-conectados/heartbeat");
	}

	private void enriquecerAutoria(LogDto dto, ContainerRequestContext requestContext) {
		LocalDateTime agora = LocalDateTime.now(ZONA_LOG);
		dto.setData(agora.toLocalDate());
		dto.setHora(agora.format(HORA_LOG));
		Object valor = requestContext == null ? null : requestContext.getProperty("ghbam.user");
		if (!(valor instanceof AuthenticatedUser user) || user.matricula() == null || user.matricula().isBlank()) return;
		String evento = dto.getEvento() == null ? "" : dto.getEvento().trim();
		String nome = user.nome() == null || user.nome().isBlank() ? "Usuario autenticado" : user.nome().trim();
		String cargo = user.cargo() == null ? "" : user.cargo().trim();
		dto.setEvento("usuario=" + nome + " | matricula=" + user.matricula()
				+ (cargo.isBlank() ? "" : " | cargo=" + cargo)
				+ " | acao=" + (evento.isBlank() ? "acao nao informada" : evento));
		dto.setUnidade(user.unidade());
	}
	}
