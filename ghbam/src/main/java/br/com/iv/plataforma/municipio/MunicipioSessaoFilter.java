package br.com.iv.plataforma.municipio;

import java.io.IOException;
import java.util.Map;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Sessao aberta num municipio nao vale no endereco de outro: o token leva o claim "municipio"
 * (TokenService) e precisa bater com o municipio do endereco. Token antigo, sem o claim,
 * conta como do municipio padrao. So age com a separacao ligada.
 */
@Provider
@Priority(Priorities.AUTHORIZATION - 10)
public class MunicipioSessaoFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(MunicipioSessaoFilter.class);

    @Inject
    MunicipioRoteamento roteamento;

    @Inject
    Instance<JsonWebToken> jwt;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        // Endereco sem municipio cadastrado (mesmo endereco para todos): quem decide o banco e' o token.
        if (!roteamento.ativa() || "OPTIONS".equalsIgnoreCase(ctx.getMethod()) || !roteamento.enderecoCadastrado()) {
            return;
        }
        JsonWebToken token = MunicipioRoteamento.tokenDaRequisicao();
        if (token == null || token.getRawToken() == null) {
            return;
        }
        Object claim = token.getClaim(MunicipioRoteamento.CLAIM);
        String doToken = claim == null ? "" : claim.toString().replace("\"", "").trim();
        if (doToken.isEmpty()) {
            doToken = roteamento.padrao();
        }
        String doEndereco = roteamento.municipioDoEndereco();
        if (doToken.equals(doEndereco)) {
            return;
        }
        LOG.warn("[MUNICIPIO] sessao de " + doToken + " usada no endereco de " + doEndereco
                + " path=" + ctx.getUriInfo().getPath());
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("erro", "SESSAO_OUTRO_MUNICIPIO",
                        "mensagem", "Esta sessão pertence a outro município. Entre novamente por este endereço."))
                .build());
    }
}
