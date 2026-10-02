package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.AfaturarDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.AfaturarService;
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

@Path("afaturar")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AfaturarController {

	@Context
	ContainerRequestContext context;

	@Inject
	AfaturarService afaturarService;

	@GET
	@Path("/data1/{d1}/data2/{d2}/unidade/{unidade}")
	public Response xlistarAfaturar(@PathParam("d1") String d1, @PathParam("d2") String d2,
			@PathParam("unidade") String unidade) {
		List<Afaturar> afaturar = afaturarService.filtarPorAfaturarPeriodo(d1, d2, unidade);
		return Response.ok(afaturar).build();
	}

	@POST
	public Response salvarFatura(AfaturarDto dto) {
		if (dto == null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("Requisição inválida: payload ausente")
					.build();
		}
		Afaturar afaturar = afaturarService.salvarFatura(dto, usuario());
		return Response.status(Response.Status.CREATED).entity(afaturar).build();
	}

	// Quem lancou a producao, para o carimbo de autoria da linha. Nao exige permissao
	// aqui de proposito: quem pode chamar a rota ja e decidido pelo RbacRequestFilter
	// (RoutePermissionCatalog: krecepcao/kfaturamento/kadm). Sem usuario no contexto o
	// carimbo fica vazio, como era antes - a gravacao nunca depende disto.
	private AuthenticatedUser usuario() {
		Object u = context == null ? null : context.getProperty("ghbam.user");
		return u instanceof AuthenticatedUser a ? a : null;
	}

}
