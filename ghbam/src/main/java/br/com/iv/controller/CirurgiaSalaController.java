package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.entity.CirurgiaSala;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.CirurgiaSalaService;

@Path("centro-cirurgico/salas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CirurgiaSalaController {

    // Sincronizado com "centro-cirurgico/salas" de RoutePermissionCatalog.java.
    private static final List<String> LEITURA = List.of("pcentrocirurgico", "kmedico", "kenfermagem",
            "pcirurgiasalas", "kadm");
    private static final List<String> ADMIN = List.of("pcirurgiasalas", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    CirurgiaSalaService salaService;

    private AuthenticatedUser user(List<String> permissoes) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(permissoes)) {
            throw new ForbiddenException();
        }
        return a;
    }

    private String unidadeDoUsuario(AuthenticatedUser u, String solicitada) {
        String doUsuario = u == null || u.unidade() == null ? "" : u.unidade().trim();
        if (!doUsuario.isBlank() && !"TODAS".equalsIgnoreCase(doUsuario)) {
            return doUsuario;
        }
        return solicitada == null || solicitada.isBlank() ? null : solicitada.trim();
    }

    private Response erro(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
    }

    @GET
    public Response listar(@QueryParam("unidade") String unidade,
            @QueryParam("somenteAtivas") Boolean somenteAtivas) {
        AuthenticatedUser u = user(LEITURA);
        boolean ativas = somenteAtivas == null || somenteAtivas;
        return Response.ok(salaService.listar(unidadeDoUsuario(u, unidade), ativas)).build();
    }

    @POST
    public Response criar(CirurgiaSala dados) {
        AuthenticatedUser u = user(ADMIN);
        try {
            return Response.status(Response.Status.CREATED).entity(salaService.criar(dados, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @PUT
    @Path("/{idsala}")
    public Response atualizar(@PathParam("idsala") int idsala, CirurgiaSala dados) {
        AuthenticatedUser u = user(ADMIN);
        try {
            return Response.ok(salaService.atualizar(idsala, dados, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @DELETE
    @Path("/{idsala}")
    public Response inativar(@PathParam("idsala") int idsala) {
        user(ADMIN);
        try {
            salaService.inativar(idsala);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }
}
