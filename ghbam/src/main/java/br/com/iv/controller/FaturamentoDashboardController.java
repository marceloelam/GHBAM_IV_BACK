package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import br.com.iv.dto.UnidadeResumoDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoCompetenciaService;
import br.com.iv.service.FaturamentoGestaoMunicipalService;
import br.com.iv.service.FaturamentoProducaoService;

@Path("faturamento/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoDashboardController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoProducaoService producaoService;

    @Inject
    FaturamentoCompetenciaService competenciaService;

    @Inject
    FaturamentoGestaoMunicipalService gestaoMunicipalService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    public Map<String, Object> dashboard(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return producaoService.montarDashboard(idcompetencia, unidadeDoDashboard(u, unidade));
    }


    // Painel de fechamento (substitui a esteira de 6 etapas como porta de entrada do
    // modulo). Uma chamada devolve tudo que a tela precisa.
    @GET
    @Path("painel")
    public Map<String, Object> painel(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return producaoService.montarPainel(idcompetencia, unidadeDoDashboard(u, unidade));
    }
    // Fase 6 - visao "todas as unidades" (secao 40 do escopo).
    @GET
    @Path("unidades")
    public List<UnidadeResumoDto> resumoPorUnidade(@QueryParam("idcompetencia") int idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return gestaoMunicipalService.resumoPorUnidade(idcompetencia, unidadeDaGestao(u, unidade));
    }

    private String unidadeRestrita(AuthenticatedUser user) {
        if (user == null || user.hasPermission("kadm")) {
            return null;
        }
        return user.unidade();
    }

    private String unidadeDaGestao(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        return unidadeSolicitada == null || unidadeSolicitada.isBlank() ? null : unidadeSolicitada;
    }

    private String unidadeDoDashboard(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        return unidadeSolicitada;
    }
}
