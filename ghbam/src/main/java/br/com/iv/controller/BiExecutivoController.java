package br.com.iv.controller;

import java.time.LocalDate;
import java.util.List;

import br.com.iv.dto.BiExecutivoDto;
import br.com.iv.service.BiExecutivoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ForbiddenException;
import br.com.iv.security.AuthenticatedUser;

@Path("bi-executivo")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BiExecutivoController {
	@Context
	ContainerRequestContext context;
	@Inject
	BiExecutivoService biExecutivoService;

	@GET
	@Path("/municipal")
	public Response painelMunicipal(@QueryParam("inicio") String inicio, @QueryParam("fim") String fim,
			@QueryParam("dias") @DefaultValue("30") int dias) {
		AuthenticatedUser user = (AuthenticatedUser) context.getProperty("ghbam.user");
		if (user == null || !user.hasAnyPermission(List.of("pcadunidades", "pbiexecutivo", "kadm"))) {
			throw new ForbiddenException();
		}
		LocalDate dataFim = parseData(fim, LocalDate.now());
		LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias - 1)));
		return Response.ok(biExecutivoService.montarPainelMunicipal(dataInicio, dataFim, user.unidade())).build();
	}

	@GET
	@Path("/unidade/{unidade}")
	public Response painel(@PathParam("unidade") String unidade, @QueryParam("inicio") String inicio,
			@QueryParam("fim") String fim, @QueryParam("dias") @DefaultValue("30") int dias) {
		LocalDate dataFim = parseData(fim, LocalDate.now());
		LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias - 1)));
		BiExecutivoDto painel = biExecutivoService.montarPainel(unidade, dataInicio, dataFim);
		return Response.ok(painel).build();
	}

	private LocalDate parseData(String valor, LocalDate padrao) {
		if (valor == null || valor.trim().isEmpty()) {
			return padrao;
		}
		return LocalDate.parse(valor);
	}
}
