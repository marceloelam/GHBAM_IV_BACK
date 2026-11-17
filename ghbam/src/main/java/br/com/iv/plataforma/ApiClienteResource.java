package br.com.iv.plataforma;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import br.com.iv.security.LoginRateLimitService;
import io.smallrye.jwt.build.Jwt;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Token de sistema integrador: client_id + client_secret → JWT de 1 h (tipo=cliente), com a unidade e as
 * permissoes cadastradas. Publico (prefixo auth, como o login); tentativas erradas caem no mesmo limitador.
 */
@Path("/auth/cliente")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiClienteResource {

    static final long VALIDADE_SEGUNDOS = 3600;

    @Inject
    ApiClienteService service;

    @Inject
    LoginRateLimitService rateLimit;

    public static class TokenRequest {
        public String client_id;
        public String client_secret;
        public String clientId;
        public String clientSecret;
    }

    @POST
    @Path("/token")
    public Response token(TokenRequest req, @Context RoutingContext routingContext) {
        String id = req == null ? "" : primeiro(req.client_id, req.clientId);
        String segredo = req == null ? "" : primeiro(req.client_secret, req.clientSecret);
        String ip = routingContext == null || routingContext.request().remoteAddress() == null ? ""
                : routingContext.request().remoteAddress().host();
        String chave = ApiClienteService.PREFIXO + id;
        if (rateLimit.segundosBloqueadoMatricula(chave) > 0 || rateLimit.segundosBloqueadoIp(ip) > 0) {
            return Response.status(429).entity(Map.of("erro", "MUITAS_TENTATIVAS")).build();
        }
        ApiClienteService.Cliente c = service.autenticar(id, segredo);
        if (c == null) {
            rateLimit.registrarFalhaMatricula(chave);
            rateLimit.registrarFalhaIp(ip);
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", "CLIENTE_INVALIDO")).build();
        }
        rateLimit.registrarSucessoMatricula(chave);
        rateLimit.registrarSucessoIp(ip);
        Set<String> grupos = new HashSet<>(c.permissoes());
        String token = Jwt.issuer("ghbam-iv")
                .upn(ApiClienteService.PREFIXO + c.clientId())
                .groups(grupos)
                .claim("tipo", "cliente")
                .claim("clientId", c.clientId())
                .claim("nome", c.nome())
                .claim("cargo", ApiClienteService.CARGO)
                .claim("unidade", c.unidade())
                .expiresIn(VALIDADE_SEGUNDOS)
                .sign();
        return Response.ok(Map.of("access_token", token, "token_type", "Bearer", "expires_in", VALIDADE_SEGUNDOS,
                "unidade", c.unidade())).build();
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b == null ? "" : b.trim();
    }
}
