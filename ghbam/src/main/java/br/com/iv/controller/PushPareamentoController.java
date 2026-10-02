package br.com.iv.controller;

import java.util.HashMap;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.PushPareamentoService;
import br.com.iv.service.ServidorService;
import br.gov.secure.AppSecrets;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/push/pareamento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PushPareamentoController {

    @Inject
    PushPareamentoService pushPareamentoService;

    @Inject
    ServidorService servidorService;

    @GET
    @Path("/gerar")
    public Response gerar(@Context ContainerRequestContext context) {
        AuthenticatedUser user = user(context);
        if (user == null || user.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("erro", "Usuário não autenticado"))
                .build();
        }

        try {
            String token = pushPareamentoService.gerarToken(user.matricula());
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("token", token);
            return Response.ok(resposta).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/token/{token}")
    public Response consultarToken(@PathParam("token") String token) {
        try {
            var matriculaOpt = pushPareamentoService.consultarToken(token);
            if (matriculaOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("erro", "Token inválido ou expirado"))
                    .build();
            }

            String matricula = matriculaOpt.get();
            // Retornar apenas informacao minima (primeiro nome, mascarado)
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("matricula", matricula);
            resposta.put("mensagem", "Confirme o pareamento deste celular à sua conta");
            return Response.ok(resposta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao consultar token"))
                .build();
        }
    }

    @POST
    @Path("/confirmar")
    public Response confirmarPareamento(
        @Context ContainerRequestContext context,
        @Context HttpHeaders httpHeaders,
        Map<String, String> request) {
        try {
            String token = request.get("token");
            String endpoint = request.get("endpoint");
            String p256dh = request.get("p256dh");
            String authKey = request.get("authKey");
            String nomeDispositivo = request.get("nomeDispositivo");
            String userAgent = httpHeaders.getHeaderString("User-Agent");

            if (token == null || token.isBlank() ||
                endpoint == null || endpoint.isBlank() ||
                p256dh == null || p256dh.isBlank() ||
                authKey == null || authKey.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campos obrigatórios faltando"))
                    .build();
            }

            pushPareamentoService.confirmarPareamento(token, endpoint, p256dh, authKey, userAgent, nomeDispositivo);
            return Response.ok(Map.of("mensagem", "Dispositivo pareado com sucesso")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao confirmar pareamento"))
                .build();
        }
    }

    @POST
    @Path("/sincronizar")
    public Response sincronizar(@Context ContainerRequestContext context, @Context HttpHeaders headers,
        Map<String, String> request) {
        AuthenticatedUser user = user(context);
        if (user == null || user.matricula().isBlank()) return Response.status(Response.Status.UNAUTHORIZED).build();
        String endpoint = request.get("endpoint");
        String p256dh = request.get("p256dh");
        String authKey = request.get("authKey");
        if (endpoint == null || endpoint.isBlank() || p256dh == null || p256dh.isBlank() || authKey == null || authKey.isBlank())
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Inscrição inválida")).build();
        pushPareamentoService.sincronizarDispositivo(user.matricula(), endpoint, p256dh, authKey,
            headers.getHeaderString("User-Agent"), request.get("nomeDispositivo"));
        return Response.ok(Map.of("mensagem", "Inscrição sincronizada")).build();
    }

    @GET
    @Path("/dispositivos")
    public Response listarDispositivos(@Context ContainerRequestContext context) {
        AuthenticatedUser user = user(context);
        if (user == null || user.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("erro", "Usuário não autenticado"))
                .build();
        }

        var dispositivos = pushPareamentoService.listarDispositivos(user.matricula()).stream()
            .map(d -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", d.getId());
                item.put("userAgent", d.getUserAgent());
                item.put("nomeDispositivo", d.getNomeDispositivo());
                item.put("ativo", Boolean.TRUE.equals(d.getAtivo()));
                item.put("criadoEm", d.getCriadoEm() != null ? d.getCriadoEm().toString() : null);
                item.put("ultimoUso", d.getUltimoUso() != null ? d.getUltimoUso().toString() : null);
                return item;
            })
            .toList();

        return Response.ok(Map.of("dispositivos", dispositivos)).build();
    }

    @DELETE
    @Path("/dispositivos/{id}")
    public Response removerDispositivo(@Context ContainerRequestContext context, @PathParam("id") Long id) {
        AuthenticatedUser user = user(context);
        if (user == null || user.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("erro", "Usuário não autenticado"))
                .build();
        }

        try {
            pushPareamentoService.removerDispositivo(user.matricula(), id);
            return Response.ok(Map.of("mensagem", "Dispositivo removido")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/vapid-public-key")
    public Response obterVapidPublicKey() {
        Map<String, String> resposta = new HashMap<>();
        resposta.put("publicKey", AppSecrets.VAPID_PUBLIC_KEY);
        return Response.ok(resposta).build();
    }

    private AuthenticatedUser user(ContainerRequestContext context) {
        Object value = context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : null;
    }
}
