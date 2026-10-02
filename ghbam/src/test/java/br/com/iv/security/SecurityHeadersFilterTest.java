package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

class SecurityHeadersFilterTest {

    @Test
    void adicionaCabecalhosDefensivosEmTodasAsRespostas() throws Exception {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ContainerResponseContext response = (ContainerResponseContext) Proxy.newProxyInstance(
                ContainerResponseContext.class.getClassLoader(),
                new Class<?>[] { ContainerResponseContext.class },
                (proxy, method, args) -> "getHeaders".equals(method.getName()) ? headers : null);

        new SecurityHeadersFilter().filter(null, response);

        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
        assertEquals("frame-ancestors 'none'; base-uri 'none'",
                headers.getFirst("Content-Security-Policy"));
        assertEquals("no-store", headers.getFirst("Cache-Control"));
    }
}
