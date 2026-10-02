package br.com.iv.controller;

import java.util.HashMap;
import java.util.Map;

import br.com.iv.service.ResetSenhaService;
import br.com.iv.service.ResetSenhaService.ValidacaoFacialException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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

@Path("/auth/esqueci-senha")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ResetSenhaController {

    @Inject
    ResetSenhaService resetSenhaService;

    @POST
    public Response criarSolicitacao(
        @Context HttpHeaders httpHeaders,
        @Context ContainerRequestContext context,
        Map<String, String> request) {

        String matricula = request.get("matricula");
        String ip = obterIPCliente(httpHeaders);
        String computador = request.get("computador");

        try {
            String consultaToken = resetSenhaService.criarSolicitacao(matricula, ip, computador);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("requestId", consultaToken);
            resposta.put("mensagem", "Enviamos uma notificação para seu(s) dispositivo(s) pareado(s). Aguarde a confirmação.");

            // Sempre retornar 200 (mesmo se matricula nao existe ou nao tem device)
            return Response.ok(resposta).build();
        } catch (Exception e) {
            // Mesmo em erro, retornar resposta genérica para nao vazar informacoes
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("requestId", "");
            resposta.put("mensagem", "Enviamos uma notificação para seu(s) dispositivo(s) pareado(s). Aguarde a confirmação.");
            return Response.ok(resposta).build();
        }
    }

    @GET
    @Path("/status/{requestId}")
    public Response consultarStatus(@PathParam("requestId") String requestId) {
        try {
            Map<String, Object> resposta = resetSenhaService.consultarStatus(requestId);
            return Response.ok(resposta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao consultar status"))
                .build();
        }
    }

    @POST
    @Path("/responder")
    public Response responder(@Context HttpHeaders headers, Map<String, Object> request) {
        try {
            String aprovarToken = (String) request.get("aprovarToken");
            Boolean aprovado = (Boolean) request.get("aprovado");

            if (aprovarToken == null || aprovarToken.isBlank() ||
                aprovado == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campos obrigatórios faltando"))
                    .build();
            }

            resetSenhaService.responder(aprovarToken, aprovado,
                (String) request.get("endpoint"), (String) request.get("p256dh"), (String) request.get("authKey"),
                headers.getHeaderString("User-Agent"), (String) request.get("nomeDispositivo"));
            return Response.ok(Map.of("mensagem", "Resposta registrada com sucesso")).build();
        } catch (ValidacaoFacialException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                    "erro", e.getMessage(),
                    "similaridade", e.getSimilaridade(),
                    "percentual", Math.round(e.getSimilaridade() * 100),
                    "minimoExigido", e.getMinimoExigido(),
                    "percentualMinimo", Math.round(e.getMinimoExigido() * 100)
                ))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao processar resposta"))
                .build();
        }
    }

    @POST
    @Path("/renovar-dispositivo")
    public Response renovarDispositivo(@Context HttpHeaders headers, Map<String, String> request) {
        try {
            String aprovarToken = request.get("aprovarToken");
            String endpoint = request.get("endpoint");
            String p256dh = request.get("p256dh");
            String authKey = request.get("authKey");
            if (aprovarToken == null || aprovarToken.isBlank() || endpoint == null || endpoint.isBlank()
                || p256dh == null || p256dh.isBlank() || authKey == null || authKey.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Campos obrigatorios faltando")).build();
            }
            resetSenhaService.renovarDispositivoAprovador(aprovarToken, endpoint, p256dh, authKey,
                headers.getHeaderString("User-Agent"), request.get("nomeDispositivo"));
            return Response.ok(Map.of("mensagem", "Inscricao renovada")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("/facial")
    public Response resetFacial(
        @Context HttpHeaders httpHeaders,
        Map<String, String> request) {
        try {
            String matricula = request.get("matricula");
            String foto = request.get("foto");
            if (foto == null || foto.isBlank()) {
                foto = request.get("imagemBase64");
            }

            if (matricula == null || matricula.isBlank() || foto == null || foto.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Matricula e foto sao obrigatorias."))
                    .build();
            }

            resetSenhaService.redefinirSenhaPadraoPorValidacaoFacial(
                matricula,
                foto,
                obterIPCliente(httpHeaders),
                request.get("computador")
            );

            return Response.ok(Map.of(
                "aprovado", true,
                "status", "RESETADO",
                "mensagem", "Senha resetada para 123456. Altere a senha no proximo acesso."
            )).build();
        } catch (ValidacaoFacialException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                    "erro", e.getMessage(),
                    "similaridade", e.getSimilaridade(),
                    "percentual", Math.round(e.getSimilaridade() * 100),
                    "minimoExigido", e.getMinimoExigido(),
                    "percentualMinimo", Math.round(e.getMinimoExigido() * 100)
                ))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao processar reset facial."))
                .build();
        }
    }

    @POST
    @Path("/confirmar")
    public Response confirmarNovaSenha(Map<String, String> request) {
        try {
            String tokenConfirmacao = request.get("tokenConfirmacao");
            String novaSenha = request.get("novaSenha");

            if (tokenConfirmacao == null || tokenConfirmacao.isBlank() ||
                novaSenha == null || novaSenha.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campos obrigatórios faltando"))
                    .build();
            }

            resetSenhaService.confirmarNovaSenha(tokenConfirmacao, novaSenha);
            return Response.ok(Map.of("mensagem", "Senha alterada com sucesso")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("erro", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao confirmar senha"))
                .build();
        }
    }

    private String obterIPCliente(HttpHeaders httpHeaders) {
        if (httpHeaders == null) return "desconhecido";
        String xff = httpHeaders.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = httpHeaders.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return "desconhecido";
    }
}
