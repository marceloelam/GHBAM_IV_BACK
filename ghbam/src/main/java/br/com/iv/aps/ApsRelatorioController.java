package br.com.iv.aps;

import static br.com.iv.aps.ApsHttp.ACS;
import static br.com.iv.aps.ApsHttp.APS;
import static br.com.iv.aps.ApsHttp.GESTAO;
import static br.com.iv.aps.ApsHttp.VACINA;
import static br.com.iv.aps.ApsHttp.executar;
import static br.com.iv.aps.ApsHttp.pode;
import static br.com.iv.aps.ApsHttp.proibido;

import java.time.LocalDate;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * GHBAM APS - relatorios (consolidados, producao, gerenciais), acompanhamentos e indicadores.
 */
@Path("api/aps/relatorios")
@Produces(MediaType.APPLICATION_JSON)
public class ApsRelatorioController {

    @Inject
    SecurityPermissionService sps;
    @Inject
    ApsRelatorioService relatorios;

    @Context
    ContainerRequestContext ctx;

    private AuthenticatedUser u() {
        return ApsHttp.usuario(ctx, sps);
    }

    private String unidade(String informada) {
        return ApsHttp.unidade(ctx, u(), informada);
    }

    private static LocalDate de(String s) {
        return ApsHttp.data(s, LocalDate.now().withDayOfMonth(1));
    }

    private static LocalDate ate(String s) {
        return ApsHttp.data(s, LocalDate.now());
    }

    @GET
    @Path("/acompanhamento")
    public Response acompanhamento(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe,
            @QueryParam("microarea") String microarea, @QueryParam("condicao") String condicao, @QueryParam("codigo") String codigo,
            @QueryParam("idadeMin") Integer idadeMin, @QueryParam("idadeMax") Integer idadeMax, @QueryParam("sexo") Integer sexo,
            @QueryParam("semAtendimentoDias") Integer semDias) {
        if (!pode(u(), APS, GESTAO, ACS)) {
            return proibido("Acompanhamentos");
        }
        return executar(() -> relatorios.listaTematica(unidade(unidade), idEquipe, microarea, condicao, codigo, idadeMin, idadeMax,
                sexo, semDias));
    }

    @GET
    @Path("/vinculados")
    public Response vinculados(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe,
            @QueryParam("microarea") String microarea) {
        if (!pode(u(), APS, GESTAO, ACS)) {
            return proibido("Acompanhamentos");
        }
        return executar(() -> relatorios.cidadaosVinculados(unidade(unidade), idEquipe, microarea));
    }

    @GET
    @Path("/consolidado/individual")
    public Response consolidadoIndividual(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe,
            @QueryParam("microarea") String microarea) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.consolidadoCadastroIndividual(unidade(unidade), idEquipe, microarea));
    }

    @GET
    @Path("/consolidado/domiciliar")
    public Response consolidadoDomiciliar(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe,
            @QueryParam("microarea") String microarea) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.consolidadoDomiciliar(unidade(unidade), idEquipe, microarea));
    }

    @GET
    @Path("/territorio")
    public Response territorio(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe) {
        if (!pode(u(), GESTAO, APS, ACS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.situacaoTerritorio(unidade(unidade), idEquipe));
    }

    /** Faturamento da APS (SISAB) por competencia: competencia=aaaa-mm (padrao: mes atual). */
    @GET
    @Path("/faturamento")
    public Response faturamento(@QueryParam("competencia") String competencia) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        LocalDate inicio = competencia != null && competencia.matches("\\d{4}-\\d{2}")
                ? LocalDate.parse(competencia + "-01") : LocalDate.now();
        return executar(() -> relatorios.faturamento(unidade(null), inicio));
    }

    @GET
    @Path("/producao/resumo")
    public Response resumo(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate,
            @QueryParam("idEquipe") Long idEquipe) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.resumoProducao(unidade(unidade), de(de), ate(ate), idEquipe));
    }

    @GET
    @Path("/producao/profissionais")
    public Response porProfissional(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate,
            @QueryParam("tipo") Integer tipo, @QueryParam("idEquipe") Long idEquipe) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.producaoPorProfissional(unidade(unidade), de(de), ate(ate), tipo, idEquipe));
    }

    @GET
    @Path("/producao/tipo/{tipo}")
    public Response detalhada(@PathParam("tipo") int tipo, @QueryParam("unidade") String unidade, @QueryParam("de") String de,
            @QueryParam("ate") String ate, @QueryParam("idEquipe") Long idEquipe) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.producaoDetalhada(unidade(unidade), de(de), ate(ate), tipo, idEquipe));
    }

    @GET
    @Path("/vacinacao")
    public Response vacinacao(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate) {
        if (!pode(u(), GESTAO, VACINA)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.vacinacao(unidade(unidade), de(de), ate(ate)));
    }

    @GET
    @Path("/absenteismo")
    public Response absenteismo(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.absenteismo(unidade(unidade), de(de), ate(ate)));
    }

    @GET
    @Path("/atendimentos")
    public Response atendimentos(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.atendimentos(unidade(unidade), de(de), ate(ate)));
    }

    @GET
    @Path("/cuidado-compartilhado")
    public Response cuidado(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Relatorios");
        }
        return executar(() -> relatorios.cuidadoCompartilhado(unidade(unidade), de(de), ate(ate)));
    }

    @GET
    @Path("/indicadores")
    public Response indicadores(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Indicadores");
        }
        return executar(() -> relatorios.indicadores(unidade(unidade), idEquipe));
    }
}
