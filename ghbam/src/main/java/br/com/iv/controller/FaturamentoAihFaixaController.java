package br.com.iv.controller;

import java.util.List;

import br.com.iv.entity.FaturamentoAihFaixa;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoAihFaixaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Faixas de numeracao de AIH concedidas pelo gestor.
 *
 * Sem faixa cadastrada o GHBAM nao consegue gerar o arquivo da AIH: o layout do SISAIH01
 * exige o NU_AIH (13 digitos) e esse numero nao e do hospital. Estes endpoints sao a porta
 * de entrada desse dado.
 */
@Path("faturamento/aih/faixas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FaturamentoAihFaixaController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoAihFaixaService faixaService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    // Cadastrar faixa e decidir de onde sai numero de AIH: mesma permissao de quem corrige
    // producao faturavel.
    private AuthenticatedUser userComPermissao() {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("pfaturamentosuscorrigir", "kadm"))) {
            throw new ForbiddenException();
        }
        return u;
    }

    private String unidadeDoUsuario(AuthenticatedUser user, String solicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        return solicitada != null && !solicitada.isBlank() ? solicitada : user.unidade();
    }

    @GET
    public Response listar(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return Response.ok(faixaService.listar(unidadeDoUsuario(u, unidade))).build();
    }

    @GET
    @Path("situacao")
    public Response situacao(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return Response.ok(faixaService.situacao(unidadeDoUsuario(u, unidade))).build();
    }

    @POST
    public Response criar(FaixaDto dto, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissao();
        try {
            FaturamentoAihFaixa faixa = faixaService.criar(unidadeDoUsuario(u, unidade),
                    dto == null ? null : dto.competencia(),
                    dto == null ? null : dto.numeroInicial(),
                    dto == null ? null : dto.numeroFinal(),
                    dto == null ? null : dto.orgaoEmissor(),
                    dto == null ? null : dto.observacao(), u);
            return Response.ok(faixa).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record FaixaDto(String competencia, String numeroInicial, String numeroFinal,
            String orgaoEmissor, String observacao) {
    }

    @POST
    @Path("alternar/{idfaixa}")
    public Response alternar(@PathParam("idfaixa") int idfaixa) {
        AuthenticatedUser u = userComPermissao();
        try {
            return Response.ok(faixaService.alternarAtiva(idfaixa, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
