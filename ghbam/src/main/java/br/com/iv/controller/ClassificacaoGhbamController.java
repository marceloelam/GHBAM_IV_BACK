package br.com.iv.controller;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ClassificacaoGhbamService;
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
import jakarta.inject.Inject;

@Path("classificacao-ghbam")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClassificacaoGhbamController {
    @Inject ClassificacaoGhbamService service;
    @Context ContainerRequestContext requestContext;

    @GET
    public Response listar(@QueryParam("status") String status) {
        AuthenticatedUser usuario = exigirLeitura();
        boolean podeVerNaoPublicado = usuario.hasAnyPermission(List.of("pclassificacaoghbam_revisar", "pclassificacaoghbam_aprovar", "pclassificacaoghbam_admin", "pclassificacaoghbam_auditar", "kadm"));
        return Response.ok(service.listar(podeVerNaoPublicado ? status : "APROVADO")).build();
    }

    @GET
    @Path("{uuid}")
    public Response buscar(@PathParam("uuid") String uuid) {
        AuthenticatedUser usuario = exigirLeitura();
        var item = service.buscar(uuid);
        if (item != null && !"APROVADO".equals(item.getStatus()) && !usuario.hasAnyPermission(List.of("pclassificacaoghbam_revisar", "pclassificacaoghbam_aprovar", "pclassificacaoghbam_admin", "pclassificacaoghbam_auditar", "kadm"))) {
            item = null;
        }
        return item == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(item).build();
    }

    @GET
    @Path("{uuid}/auditoria")
    public Response auditoria(@PathParam("uuid") String uuid) {
        AuthenticatedUser usuario = exigir("pclassificacaoghbam_auditar", "pclassificacaoghbam_revisar", "pclassificacaoghbam_aprovar", "pclassificacaoghbam_admin", "kadm");
        var item = service.buscar(uuid);
        if (item == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(service.auditoria(item.getUuid())).build();
    }

    @POST
    public Response criar(JsonNode body) {
        AuthenticatedUser usuario = exigir("pclassificacaoghbam_revisar", "kadm");
        return tratar(() -> service.salvar(body, usuario), Response.Status.CREATED);
    }

    @PUT
    @Path("{uuid}")
    public Response atualizar(@PathParam("uuid") String uuid, JsonNode body) {
        AuthenticatedUser usuario = exigir("pclassificacaoghbam_revisar", "kadm");
        com.fasterxml.jackson.databind.node.ObjectNode copia = body != null && body.isObject()
                ? body.deepCopy() : new com.fasterxml.jackson.databind.node.JsonNodeFactory(false).objectNode();
        copia.put("uuid", uuid);
        return tratar(() -> service.salvar(copia, usuario), Response.Status.OK);
    }

    @POST
    @Path("{uuid}/revisar")
    public Response revisar(@PathParam("uuid") String uuid) {
        return tratar(() -> service.enviarRevisao(uuid, exigir("pclassificacaoghbam_revisar", "kadm")), Response.Status.OK);
    }

    @POST
    @Path("{uuid}/aprovar")
    public Response aprovar(@PathParam("uuid") String uuid) {
        return tratar(() -> service.aprovar(uuid, exigir("pclassificacaoghbam_aprovar", "kadm")), Response.Status.OK);
    }

    @POST
    @Path("{uuid}/rejeitar")
    public Response rejeitar(@PathParam("uuid") String uuid, JsonNode body) {
        AuthenticatedUser usuario = exigir("pclassificacaoghbam_aprovar", "kadm");
        String motivo = body == null ? "" : body.path("motivo").asText("");
        return tratar(() -> service.rejeitar(uuid, motivo, usuario), Response.Status.OK);
    }

    @POST
    @Path("{uuid}/nova-versao")
    public Response novaVersao(@PathParam("uuid") String uuid) {
        return tratar(() -> service.novaVersao(uuid, exigir("pclassificacaoghbam_revisar", "kadm")), Response.Status.CREATED);
    }

    @DELETE
    @Path("{uuid}")
    public Response arquivar(@PathParam("uuid") String uuid, @QueryParam("motivo") String motivo) {
        AuthenticatedUser usuario = exigir("pclassificacaoghbam_admin", "kadm");
        return tratar(() -> service.arquivar(uuid, motivo, usuario), Response.Status.OK);
    }

    private AuthenticatedUser exigirLeitura() { return exigir("pclassificacaoghbam", "kenfermagem", "kadm", "pclassificacaoghbam_auditar"); }

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
        catch (IllegalStateException e) { return Response.status(Response.Status.CONFLICT).entity(java.util.Map.of("erro", e.getMessage())).build(); }
        catch (IllegalArgumentException e) { return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("erro", e.getMessage())).build(); }
    }
}
