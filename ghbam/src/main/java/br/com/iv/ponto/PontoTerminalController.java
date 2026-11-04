package br.com.iv.ponto;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Terminal de ponto (kiosk sem login), sob "auth/" como o reset de senha. A
 * identidade vem do rosto com prova de vida comparado a foto cadastrada.
 */
@Path("auth/ponto/terminal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PontoTerminalController {

    @Inject
    PontoService service;

    @Context
    HttpHeaders headers;

    @Inject
    br.com.iv.biofingertech.requisitarCapturaDoBackPrincipal digitalLogin;

    @POST
    @Path("desafio")
    public Response desafio(Map<String, Object> body) {
        try {
            var d = service.desafio(body == null ? null : String.valueOf(body.get("matricula")));
            return Response.ok(Map.of("desafioId", d.id(), "etapas", d.etapas(), "validadeSegundos", d.validadeSegundos())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    /** Sem matricula: o rosto identifica o servidor e ja devolve o desafio de prova de vida. */
    @POST
    @Path("identificar")
    public Response identificar(Map<String, Object> body) {
        try {
            String imagem = body == null || body.get("imagem") == null ? null : String.valueOf(body.get("imagem"));
            return Response.ok(service.identificar(imagem, ip())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("registrar")
    public Response registrar(Map<String, Object> body) {
        try {
            Object imgs = body == null ? null : body.get("imagens");
            List<String> imagens = imgs instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
            String unidade = body == null || body.get("unidade") == null ? null : String.valueOf(body.get("unidade"));
            return Response.ok(service.registrar(
                    body == null ? null : String.valueOf(body.get("matricula")),
                    body == null ? null : String.valueOf(body.get("desafioId")),
                    imagens, unidade, ip())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    /** Digital: o agente local do leitor entrega a imagem; a identificacao 1:N e feita no servidor. */
    @POST
    @Path("biometria")
    public Response biometria(Map<String, Object> body, @Context io.vertx.ext.web.RoutingContext routingContext) {
        try {
            String imagem = body == null || body.get("imagem") == null ? null : String.valueOf(body.get("imagem"));
            Double dpi = body != null && body.get("dpi") instanceof Number n ? n.doubleValue() : null;
            String unidade = body == null || body.get("unidade") == null ? null : String.valueOf(body.get("unidade"));
            String fir = body == null || body.get("fir") == null ? null : String.valueOf(body.get("fir"));
            return Response.ok(service.registrarBiometria(imagem, dpi, unidade, ip(),
                    () -> digitalLogin.identificarPeloAgenteNitgen(fir, routingContext))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    private String ip() {
        String xff = headers == null ? null : headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = headers == null ? null : headers.getHeaderString("X-Real-IP");
        return real == null || real.isBlank() ? "desconhecido" : real.trim();
    }
}
