package br.com.iv.controller;

import java.time.LocalDate;
import java.util.Map;

import br.com.iv.service.ConformidadePendenciaBaixaService;
import br.com.iv.service.JornadaPaService;
import jakarta.ws.rs.DefaultValue;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("conformidade-pendencia")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConformidadePendenciaBaixaController {

    @Inject
    ConformidadePendenciaBaixaService service;

    @Inject
    JornadaPaService jornadaPaService;

    @GET
    @Path("/bam/{idbam}")
    public Response listar(
            @PathParam("idbam") int idbam,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        service.validarAcessoBam(idbam, unidadeLogada);
        return Response.ok(service.listarPorBam(idbam).stream().map(service::toMap).toList()).build();
    }

    @GET
    @Path("/relatorio")
    public Response relatorio(
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("2") int dias,
            @QueryParam("unidade") String unidade,
            @QueryParam("limite") @DefaultValue("50") int limite,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias) - 1L));
        String unidadeEfetiva = unidadeLogada == null || unidadeLogada.isBlank() ? unidade : unidadeLogada;
        return Response.ok(service.relatorio(dataInicio, dataFim, unidadeEfetiva, limite)).build();
    }

    @POST
    @Path("/baixa")
    public Response registrar(
            Map<String, Object> dto,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        Map<String, Object> baixa = service.toMap(service.registrar(dto, unidadeLogada));
        jornadaPaService.limparCacheConformidade();
        return Response.ok(baixa).build();
    }

    private LocalDate parseData(String valor, LocalDate padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        return LocalDate.parse(valor);
    }
}
