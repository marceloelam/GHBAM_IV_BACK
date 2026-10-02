package br.com.iv.controller;

import br.com.iv.entity.EvidenciaAssistencial;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.EvidenciaAssistencialService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("assistencial/{modulo}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvidenciaAssistencialController {

    private static final Map<String, List<String>> LEITURA = Map.of(
        "CME", List.of("pcme", "pcentrocirurgico", "kadm"),
        "HEMOTERAPIA", List.of("phemoterapia", "kenfermagem", "kmedico", "kadm"),
        "SND", List.of("psnd", "knutricao", "kenfermagem", "kadm"),
        "RNDS", List.of("prnds", "pintegracaosus", "kadm")
    );

    // HEMOTERAPIA e SND sincronizados com "assistencial/hemoterapia" e
    // "assistencial/snd" de RoutePermissionCatalog.java, que ja liberam
    // kenfermagem/kmedico para a mesma rota (GET e POST/PATCH).
    private static final Map<String, List<String>> ESCRITA = Map.of(
        "CME", List.of("pcme", "pcentrocirurgico", "kadm"),
        "HEMOTERAPIA", List.of("phemoterapia", "kenfermagem", "kmedico", "kadm"),
        "SND", List.of("psnd", "knutricao", "kenfermagem", "kadm"),
        "RNDS", List.of("prnds", "pintegracaosus", "kadm")
    );

    @Context
    ContainerRequestContext context;

    @Inject
    EvidenciaAssistencialService service;

    private AuthenticatedUser user(String modulo, Map<String, List<String>> permissoesPorModulo) {
        String chave = modulo == null ? "" : modulo.trim().toUpperCase();
        Object u = context.getProperty("ghbam.user");
        List<String> permissoes = permissoesPorModulo.get(chave);
        if (permissoes == null || !(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(permissoes)) {
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

    @GET
    public Response listar(
        @PathParam("modulo") String modulo,
        @QueryParam("unidade") String unidade,
        @QueryParam("status") String status,
        @QueryParam("origemModulo") String origemModulo,
        @QueryParam("origemId") String origemId
    ) {
        AuthenticatedUser u = user(modulo, LEITURA);
        try {
            return Response.ok(service.listar(modulo, unidadeDoUsuario(u, unidade), status, origemModulo, origemId)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    public Response criar(@PathParam("modulo") String modulo, EvidenciaAssistencial registro) {
        AuthenticatedUser u = user(modulo, ESCRITA);
        if (registro.unidade == null || registro.unidade.isBlank()) {
            registro.unidade = unidadeDoUsuario(u, null);
        }
        if (registro.usuarioRegistro == null || registro.usuarioRegistro.isBlank()) {
            registro.usuarioRegistro = u.matricula();
        }
        try {
            return Response.status(Response.Status.CREATED).entity(service.criar(modulo, registro)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("{id}/status")
    public Response atualizarStatus(
        @PathParam("modulo") String modulo,
        @PathParam("id") Long id,
        Map<String, String> payload
    ) {
        AuthenticatedUser u = user(modulo, ESCRITA);
        try {
            String status = payload == null ? null : payload.get("status");
            EvidenciaAssistencial atualizado = service.atualizarStatus(modulo, id, status, u.matricula());
            if (atualizado == null) {
                throw new NotFoundException();
            }
            return Response.ok(atualizado).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("{id}/processar-rnds")
    public Response processarRnds(
        @PathParam("modulo") String modulo,
        @PathParam("id") Long id
    ) {
        AuthenticatedUser u = user(modulo, ESCRITA);
        if (!"RNDS".equalsIgnoreCase(modulo)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Processamento disponivel apenas para RNDS.")).build();
        }
        try {
            EvidenciaAssistencial atualizado = service.processarRnds(id, u);
            if (atualizado == null) {
                throw new NotFoundException();
            }
            return Response.ok(atualizado).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }
}
