package br.com.iv.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.ConformidadeFaturamentoService;
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
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
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

    @Inject
    ConformidadeFaturamentoService faturamentoService;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

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

    /**
     * Gera a producao de atendimento medico para BAMs atendidos sem faturamento.
     * Corpo: {"bams": [521347, 521334]}. Cada BAM volta com seu status
     * (GERADO, JA_FATURADO, SEM_EVIDENCIA, OUTRA_UNIDADE, NAO_ENCONTRADO, ERRO).
     */
    @POST
    @Path("/faturamento-automatico")
    public Response faturamentoAutomatico(
            Map<String, Object> dto,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        Object bruto = dto == null ? null : dto.get("bams");
        List<Integer> bams = bruto instanceof List<?> lista
                ? lista.stream()
                        .map(item -> {
                            if (item instanceof Number numero) return numero.intValue();
                            try {
                                return Integer.valueOf(String.valueOf(item).trim());
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                        .filter(item -> item != null && item > 0)
                        .toList()
                : List.of();
        return Response.ok(faturamentoService.corrigirLote(bams, unidadeLogada, currentUser())).build();
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : securityPermissionService.resolve(requestContext);
    }

    private LocalDate parseData(String valor, LocalDate padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        return LocalDate.parse(valor);
    }
}
