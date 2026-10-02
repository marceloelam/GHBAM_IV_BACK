package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.CirurgiaListaEsperaDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.CirurgiaListaEsperaService;

@Path("centro-cirurgico/lista-espera")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CirurgiaListaEsperaController {

    // Sincronizado com "centro-cirurgico/lista-espera" de RoutePermissionCatalog.java.
    private static final List<String> LEITURA = List.of("pcentrocirurgico", "kmedico", "kenfermagem",
            "pcirurgialistaespera", "pcirurgiaagendar", "kadm");
    private static final List<String> GERIR = List.of("pcirurgialistaespera", "pcirurgiaagendar", "pcentrocirurgico",
            "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    CirurgiaListaEsperaService listaEsperaService;

    private AuthenticatedUser user(List<String> permissoes) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(permissoes)) {
            throw new ForbiddenException();
        }
        return a;
    }

    private String unidadeDoUsuario(AuthenticatedUser u, String solicitada) {
        String doUsuario = u == null || u.unidade() == null ? "" : u.unidade().trim();
        if (!doUsuario.isBlank() && !"TODAS".equalsIgnoreCase(doUsuario)) {
            return doUsuario;
        }
        return solicitada == null || solicitada.isBlank() ? null : solicitada.trim();
    }

    private Response erro(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
    }

    @GET
    public Response fila(@QueryParam("unidade") String unidade, @QueryParam("status") String status,
            @QueryParam("especialidade") String especialidade) {
        AuthenticatedUser u = user(LEITURA);
        String und = unidadeDoUsuario(u, unidade);
        String st = status == null || status.isBlank() ? "AGUARDANDO" : status;
        return Response.ok(Map.of(
                "itens", listaEsperaService.fila(und, st, especialidade),
                "aguardando", listaEsperaService.contarAguardando(und))).build();
    }

    @POST
    public Response incluir(CirurgiaListaEsperaDto dto) {
        AuthenticatedUser u = user(GERIR);
        try {
            if (dto != null && (dto.getUnidade() == null || dto.getUnidade().isBlank())) {
                dto.setUnidade(unidadeDoUsuario(u, null));
            }
            return Response.status(Response.Status.CREATED)
                    .entity(listaEsperaService.incluir(dto, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @PUT
    @Path("/{idespera}/prioridade")
    public Response atualizarPrioridade(@PathParam("idespera") int idespera, Map<String, String> corpo) {
        user(GERIR);
        try {
            String prioridade = corpo == null ? null : corpo.get("prioridade");
            String justificativa = corpo == null ? null : corpo.get("justificativa");
            return Response.ok(listaEsperaService.atualizarPrioridade(idespera, prioridade, justificativa)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @DELETE
    @Path("/{idespera}")
    public Response cancelar(@PathParam("idespera") int idespera, @QueryParam("motivo") String motivo) {
        AuthenticatedUser u = user(GERIR);
        try {
            return Response.ok(listaEsperaService.cancelar(idespera, motivo, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }
}
