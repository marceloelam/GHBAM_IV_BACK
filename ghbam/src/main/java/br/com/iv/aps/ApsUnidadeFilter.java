package br.com.iv.aps;

import java.io.IOException;

import br.com.iv.security.SecurityPermissionService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * O G-PEC opera sempre na unidade logada: guarda a unidade do JWT da sessao para
 * {@link ApsHttp#unidade}, que ignora unidade vinda de query/header quando ela existe.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class ApsUnidadeFilter implements ContainerRequestFilter {

    static final String PROPRIEDADE = "aps.unidadeSessao";

    @Inject
    SecurityPermissionService security;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path = ctx.getUriInfo().getPath();
        if (path == null || !path.replaceFirst("^/+", "").startsWith("api/aps")) {
            return;
        }
        String unidade = security.unidadeDaSessao(ctx);
        if (!unidade.isBlank()) {
            ctx.setProperty(PROPRIEDADE, unidade);
        }
    }
}
