package br.com.iv.controller;

import br.com.iv.dto.MigracaoDadosPlanoDto;
import br.com.iv.dto.MigracaoDadosExportacaoDto;
import br.com.iv.dto.MigracaoDadosBancoExternoDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.MigracaoDadosService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.List;

@Path("api/migracao-dados")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MigracaoDadosController {

    @Inject
    MigracaoDadosService service;

    @POST
    @Path("plano/analisar")
    public Response analisarPlano(MigracaoDadosPlanoDto plano, @Context ContainerRequestContext context) {
        try {
            AuthenticatedUser usuario = user(context);
            if (!usuario.hasAnyPermission(List.of("kadm", "pmigracaodados"))) {
                return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Usuario sem permissao para migracao de dados")).build();
            }
            return Response.ok(service.analisarPlano(plano, usuario)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("exportacao/preparar")
    public Response prepararExportacao(MigracaoDadosExportacaoDto pedido, @Context ContainerRequestContext context) {
        try {
            AuthenticatedUser usuario = user(context);
            if (!usuario.hasAnyPermission(List.of("kadm", "pmigracaodados"))) {
                return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Usuario sem permissao para exportacao de dados")).build();
            }
            return Response.ok(service.prepararExportacao(pedido, usuario)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("banco-externo/preparar")
    public Response prepararBancoExterno(MigracaoDadosBancoExternoDto pedido, @Context ContainerRequestContext context) {
        try {
            AuthenticatedUser usuario = user(context);
            if (!usuario.hasAnyPermission(List.of("kadm", "pmigracaodados"))) {
                return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Usuario sem permissao para migracao por banco externo")).build();
            }
            return Response.ok(service.prepararBancoExterno(pedido, usuario)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    private AuthenticatedUser user(ContainerRequestContext context) {
        Object value = context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : AuthenticatedUser.empty();
    }
}
