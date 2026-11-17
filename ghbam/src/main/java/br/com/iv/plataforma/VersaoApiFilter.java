package br.com.iv.plataforma;

import java.io.IOException;
import java.net.URI;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

/**
 * Versao da API para integradores: /v1/... responde como a rota atual (sem versao). Roda antes do
 * roteamento, entao RBAC, escopo por unidade e auditoria veem o caminho ja sem o prefixo.
 * O frontend segue sem versao. Quando uma rota mudar de forma incompativel, a v1 deve continuar
 * respondendo como antes (ex.: recurso proprio em /v2/... ou adaptacao aqui).
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class VersaoApiFilter implements ContainerRequestFilter {

    static final String PREFIXO = "v1";
    public static final String PROPRIEDADE = "ghbam.api.versao";

    /** Caminho relativo sem o prefixo de versao ("" = raiz), ou null quando nao usa /v1. */
    static String semVersao(String relativo) {
        if (relativo == null) {
            return null;
        }
        String c = relativo.startsWith("/") ? relativo.substring(1) : relativo;
        if (c.equals(PREFIXO) || c.equals(PREFIXO + "/")) {
            return "";
        }
        return c.startsWith(PREFIXO + "/") ? c.substring(PREFIXO.length() + 1) : null;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        URI base = requestContext.getUriInfo().getBaseUri();
        URI pedido = requestContext.getUriInfo().getRequestUri();
        String novo = semVersao(base.relativize(pedido).getRawPath());
        if (novo == null) {
            return;
        }
        String query = pedido.getRawQuery();
        requestContext.setProperty(PROPRIEDADE, PREFIXO);
        requestContext.setRequestUri(base.resolve(novo + (query == null ? "" : "?" + query)));
    }
}
