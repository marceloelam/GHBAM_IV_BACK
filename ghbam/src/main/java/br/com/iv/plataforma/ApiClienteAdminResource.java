package br.com.iv.plataforma;

import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Cadastro de sistemas integradores. So o superusuario (administrador da plataforma). */
@Path("/plataforma/clientes-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiClienteAdminResource {

    private static final List<String> PLATAFORMA = List.of("ksuperusuario", "superusuario");

    @Inject
    ApiClienteService service;

    public static class NovoClienteRequest {
        public String nome;
        public String unidade;
        public String permissoes;
    }

    @GET
    public Response listar(@Context ContainerRequestContext ctx) {
        if (!plataforma(ctx)) {
            return negado();
        }
        return Response.ok(service.listar()).build();
    }

    @POST
    public Response criar(NovoClienteRequest req, @Context ContainerRequestContext ctx) {
        if (!plataforma(ctx)) {
            return negado();
        }
        try {
            ApiClienteService.Criado criado = service.criar(req == null ? null : req.nome,
                    req == null ? null : req.unidade, req == null ? null : req.permissoes, usuario(ctx).matricula());
            // O segredo so aparece aqui, uma vez: guardar no sistema integrador.
            return Response.status(Response.Status.CREATED).entity(Map.of("clientId", criado.clientId(),
                    "clientSecret", criado.clientSecret(),
                    "aviso", "Guarde o clientSecret agora: ele nao pode ser consultado depois.")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{clientId}")
    public Response desativar(@PathParam("clientId") String clientId, @Context ContainerRequestContext ctx) {
        if (!plataforma(ctx)) {
            return negado();
        }
        return service.desativar(clientId) ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static AuthenticatedUser usuario(ContainerRequestContext ctx) {
        return ctx != null && ctx.getProperty("ghbam.user") instanceof AuthenticatedUser u ? u : AuthenticatedUser.empty();
    }

    // Defesa em profundidade: o RBAC ja exige superusuario nessa rota; cliente de integracao nunca gerencia clientes.
    static boolean plataforma(ContainerRequestContext ctx) {
        AuthenticatedUser u = usuario(ctx);
        return !u.matricula().isBlank() && !u.matricula().startsWith(ApiClienteService.PREFIXO)
                && u.hasAnyPermission(PLATAFORMA);
    }

    private static Response negado() {
        return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Somente o superusuario")).build();
    }
}
