package br.com.iv.security;

import java.io.IOException;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Aplica uma linha de base defensiva a todas as respostas HTTP da API.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        responseContext.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        responseContext.getHeaders().putSingle("X-Frame-Options", "DENY");
        responseContext.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        responseContext.getHeaders().putSingle(
                "Content-Security-Policy",
                "frame-ancestors 'none'; base-uri 'none'");
        responseContext.getHeaders().putSingle("Cache-Control", "no-store");
    }
}
