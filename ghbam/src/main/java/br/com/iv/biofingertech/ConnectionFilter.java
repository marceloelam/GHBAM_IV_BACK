package br.com.iv.biofingertech;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ConnectionFilter implements ContainerRequestFilter, ContainerResponseFilter {

    // Método obrigatório da interface ContainerRequestFilter
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Lógica para processar a requisição (pode ficar vazio se não for necessário)
    }

    // Método obrigatório da interface ContainerResponseFilter
    @Override
    public void filter(ContainerRequestContext requestContext, 
                      ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().add("Connection", "keep-alive");
    }
}