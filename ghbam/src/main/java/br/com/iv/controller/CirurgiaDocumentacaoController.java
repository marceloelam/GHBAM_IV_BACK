package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.entity.CirurgiaDescricao;
import br.com.iv.entity.CirurgiaFichaAnestesica;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.CirurgiaDocumentacaoService;

@Path("centro-cirurgico/documentacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CirurgiaDocumentacaoController {

    // Sincronizado com "centro-cirurgico/documentacao" de RoutePermissionCatalog.java.
    private static final List<String> LEITURA = List.of("pcentrocirurgico", "kmedico", "kenfermagem",
            "pcirurgiadescrever", "pcirurgiaanestesia", "kadm");
    private static final List<String> DESCREVER = List.of("pcirurgiadescrever", "kmedico", "kadm");
    private static final List<String> ANESTESIA = List.of("pcirurgiaanestesia", "kmedico", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    CirurgiaDocumentacaoService documentacaoService;

    private AuthenticatedUser user(List<String> permissoes) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(permissoes)) {
            throw new ForbiddenException();
        }
        return a;
    }

    private Response erro(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
    }

    @GET
    @Path("/{idcirurgia}/descricao")
    public Response obterDescricao(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(documentacaoService.obterDescricao(idcirurgia)).build();
    }

    @PUT
    @Path("/{idcirurgia}/descricao")
    public Response salvarDescricao(@PathParam("idcirurgia") int idcirurgia, CirurgiaDescricao dados) {
        AuthenticatedUser u = user(DESCREVER);
        try {
            return Response.ok(documentacaoService.salvarDescricao(idcirurgia, dados, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @POST
    @Path("/{idcirurgia}/descricao/assinar")
    public Response assinarDescricao(@PathParam("idcirurgia") int idcirurgia) {
        AuthenticatedUser u = user(DESCREVER);
        try {
            return Response.ok(documentacaoService.assinarDescricao(idcirurgia, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/{idcirurgia}/ficha-anestesica")
    public Response obterFicha(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(documentacaoService.obterFicha(idcirurgia)).build();
    }

    @PUT
    @Path("/{idcirurgia}/ficha-anestesica")
    public Response salvarFicha(@PathParam("idcirurgia") int idcirurgia, CirurgiaFichaAnestesica dados) {
        AuthenticatedUser u = user(ANESTESIA);
        try {
            return Response.ok(documentacaoService.salvarFicha(idcirurgia, dados, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }
}
