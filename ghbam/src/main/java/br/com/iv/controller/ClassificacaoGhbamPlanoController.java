package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ClassificacaoGhbamPlanoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Plano de cuidados da Classificação GHBAM.
 * Planejar/avaliar/encerrar: permissão "Classificação própria GHBAM" (enfermeiro) ou kadm.
 * Registrar execução de ações: também liberado a kenfermagem (equipe de enfermagem).
 */
@Path("classificacao-ghbam-plano")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClassificacaoGhbamPlanoController {
    private static final String[] LEITURA = { "pclassificacaoghbam", "kenfermagem", "ktcenfermagem", "kadm", "pclassificacaoghbam_auditar" };
    private static final String[] PLANEJAR = { "pclassificacaoghbam", "kadm" };
    private static final String[] EXECUTAR = { "pclassificacaoghbam", "kenfermagem", "ktcenfermagem", "kadm" };

    @Inject ClassificacaoGhbamPlanoService service;
    @Context ContainerRequestContext requestContext;

    @GET
    public Response listar(@QueryParam("bam") Integer bam) {
        exigir(LEITURA);
        if (bam == null || bam <= 0) return Response.ok(List.of()).build();
        return Response.ok(service.listar(bam)).build();
    }

    @GET
    @Path("{uuid}/execucoes")
    public Response execucoes(@PathParam("uuid") String uuid) {
        exigir(LEITURA);
        return tratar(() -> service.execucoes(uuid), Response.Status.OK);
    }

    @POST
    public Response incluir(JsonNode body) {
        AuthenticatedUser usuario = exigir(PLANEJAR);
        return tratar(() -> service.incluir(body, usuario), Response.Status.CREATED);
    }

    @POST
    @Path("{uuid}/execucao")
    public Response execucao(@PathParam("uuid") String uuid, JsonNode body) {
        AuthenticatedUser usuario = exigir(EXECUTAR);
        return tratar(() -> service.registrarExecucao(uuid, campo(body, "situacao"), campo(body, "observacao"), usuario), Response.Status.OK);
    }

    @POST
    @Path("{uuid}/avaliacao")
    public Response avaliacao(@PathParam("uuid") String uuid, JsonNode body) {
        AuthenticatedUser usuario = exigir(PLANEJAR);
        return tratar(() -> service.avaliar(uuid, campo(body, "avaliacao"), campo(body, "observacao"), usuario), Response.Status.OK);
    }

    @POST
    @Path("{uuid}/encerrar")
    public Response encerrar(@PathParam("uuid") String uuid, JsonNode body) {
        AuthenticatedUser usuario = exigir(PLANEJAR);
        return tratar(() -> service.encerrar(uuid, campo(body, "motivo"), usuario), Response.Status.OK);
    }

    private String campo(JsonNode body, String nome) {
        return body == null ? "" : body.path(nome).asText("");
    }

    private AuthenticatedUser exigir(String... permissoes) {
        Object usuario = requestContext.getProperty("ghbam.user");
        if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
            throw new jakarta.ws.rs.NotAuthorizedException("Bearer");
        }
        if (!autenticado.hasAnyPermission(List.of(permissoes))) throw new ForbiddenException("Permissao insuficiente");
        return autenticado;
    }

    private Response tratar(java.util.function.Supplier<Object> acao, Response.Status sucesso) {
        try { return Response.status(sucesso).entity(acao.get()).build(); }
        catch (IllegalStateException e) { return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build(); }
        catch (IllegalArgumentException e) { return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build(); }
    }
}
