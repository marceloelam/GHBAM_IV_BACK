package br.com.iv.controller;

import java.util.Map;
import java.time.LocalDate;

import br.com.iv.dto.PepModeloAnotacaoDto;
import br.com.iv.dto.PepAnotacaoRascunhoDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ConformidadePendenciaBaixaService;
import br.com.iv.service.PepModeloAnotacaoService;
import br.com.iv.service.PepAnotacaoRascunhoService;
import br.com.iv.service.JornadaPaService;
import jakarta.ws.rs.DefaultValue;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
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

@Path("jornada-pa")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class JornadaPaController {

    @Inject
    JornadaPaService jornadaPaService;

    @Inject
    ConformidadePendenciaBaixaService pendenciaBaixaService;

    @Inject
    PepAnotacaoRascunhoService anotacaoRascunhoService;

    @Inject
    PepModeloAnotacaoService modeloAnotacaoService;

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/bam/{idbam}")
    public Response consultarPorBam(@PathParam("idbam") int idbam) {
        Map<String, Object> jornada = jornadaPaService.montarPorBam(idbam);
        if (Boolean.FALSE.equals(jornada.get("encontrado"))) {
            return Response.status(Response.Status.NOT_FOUND).entity(jornada).build();
        }
        return Response.ok(jornada).build();
    }

    @GET
    @Path("/pep/{idbam}")
    public Response pepPorBam(@PathParam("idbam") int idbam) {
        Map<String, Object> pep = jornadaPaService.montarPepPorBam(idbam);
        if (Boolean.FALSE.equals(pep.get("encontrado"))) {
            return Response.status(Response.Status.NOT_FOUND).entity(pep).build();
        }
        return Response.ok(pep).build();
    }

    @GET
    @Path("/pep/{idbam}/anotacoes-rascunho")
    public Response listarAnotacoesRascunho(@PathParam("idbam") int idbam) {
        return Response.ok(anotacaoRascunhoService.listarPorBam(idbam, usuarioAtual())).build();
    }

    @POST
    @Path("/pep/{idbam}/anotacoes-rascunho")
    public Response salvarAnotacaoRascunho(@PathParam("idbam") int idbam, PepAnotacaoRascunhoDto dto) {
        try {
            if (dto == null) dto = new PepAnotacaoRascunhoDto();
            dto.setIdbam(idbam);
            return Response.ok(anotacaoRascunhoService.toMap(anotacaoRascunhoService.salvar(dto, usuarioAtual()), usuarioAtual())).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @PUT
    @Path("/pep/anotacoes-rascunho/{id}")
    public Response atualizarAnotacaoRascunho(@PathParam("id") long id, PepAnotacaoRascunhoDto dto) {
        try {
            return Response.ok(anotacaoRascunhoService.toMap(anotacaoRascunhoService.atualizar(id, dto, usuarioAtual()), usuarioAtual())).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @DELETE
    @Path("/pep/anotacoes-rascunho/{id}")
    public Response removerAnotacaoRascunho(@PathParam("id") long id) {
        anotacaoRascunhoService.remover(id, usuarioAtual());
        return Response.noContent().build();
    }

    @GET
    @Path("/pep/modelos-anotacao")
    public Response listarModelosAnotacao() {
        return Response.ok(modeloAnotacaoService.listar(usuarioAtual())).build();
    }

    @POST
    @Path("/pep/modelos-anotacao")
    public Response criarModeloAnotacao(PepModeloAnotacaoDto dto) {
        try {
            AuthenticatedUser usuario = usuarioAtual();
            return Response.ok(modeloAnotacaoService.toMap(modeloAnotacaoService.criar(dto, usuario), usuario)).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @PUT
    @Path("/pep/modelos-anotacao/{id}")
    public Response atualizarModeloAnotacao(@PathParam("id") long id, PepModeloAnotacaoDto dto) {
        try {
            AuthenticatedUser usuario = usuarioAtual();
            return Response.ok(modeloAnotacaoService.toMap(modeloAnotacaoService.atualizar(id, dto, usuario), usuario)).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @DELETE
    @Path("/pep/modelos-anotacao/{id}")
    public Response removerModeloAnotacao(@PathParam("id") long id) {
        modeloAnotacaoService.remover(id, usuarioAtual());
        return Response.noContent().build();
    }

    @GET
    @Path("/conformidade/{idbam}")
    public Response conformidade(@PathParam("idbam") int idbam) {
        Map<String, Object> conformidade = jornadaPaService.montarConformidadeAtendimento(idbam);
        if (Boolean.FALSE.equals(conformidade.get("encontrado"))) {
            return Response.status(Response.Status.NOT_FOUND).entity(conformidade).build();
        }
        return Response.ok(conformidade).build();
    }

    @GET
    @Path("/conformidade")
    public Response filaConformidade(
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("2") int dias,
            @QueryParam("unidade") String unidade,
            @QueryParam("risco") String risco,
            @QueryParam("status") String status,
            @QueryParam("limite") @DefaultValue("30") int limite,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias) - 1L));
        String unidadeEfetiva = unidadeLogada == null || unidadeLogada.isBlank() ? unidade : unidadeLogada;
        return Response.ok(jornadaPaService.montarFilaPendenciasRapida(dataInicio, dataFim, unidadeEfetiva, risco, status, limite)).build();
    }

    @GET
    @Path("/pendencias")
    public Response filaPendencias(
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("2") int dias,
            @QueryParam("unidade") String unidade,
            @QueryParam("risco") String risco,
            @QueryParam("status") String status,
            @QueryParam("limite") @DefaultValue("50") int limite,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias) - 1L));
        String unidadeEfetiva = unidadeLogada == null || unidadeLogada.isBlank() ? unidade : unidadeLogada;
        return Response.ok(jornadaPaService.montarFilaConformidade(dataInicio, dataFim, unidadeEfetiva, risco, status, limite)).build();
    }

    @GET
    @Path("/conformidade-executivo")
    public Response conformidadeExecutivo(
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("2") int dias,
            @QueryParam("unidade") String unidade,
            @QueryParam("limitePendencias") @DefaultValue("30") int limitePendencias,
            @QueryParam("limiteBaixas") @DefaultValue("80") int limiteBaixas,
            @QueryParam("refresh") @DefaultValue("false") boolean refresh,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias) - 1L));
        String unidadeEfetiva = unidadeLogada == null || unidadeLogada.isBlank() ? unidade : unidadeLogada;
        return Response.ok(jornadaPaService.montarResumoExecutivoConformidade(
                dataInicio,
                dataFim,
                unidadeEfetiva,
                limitePendencias,
                limiteBaixas,
                refresh)).build();
    }

    @GET
    @Path("/bi")
    public Response bi(
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("7") int dias,
            @QueryParam("unidade") String unidade,
            @QueryParam("risco") String risco,
            @QueryParam("limite") @DefaultValue("50") int limite) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(Math.max(1, dias) - 1L));
        return Response.ok(jornadaPaService.montarBi(dataInicio, dataFim, unidade, risco, limite)).build();
    }

    @GET
    @Path("/riscos")
    public Response riscos() {
        return Response.ok(jornadaPaService.listarRiscos()).build();
    }

    @GET
    @Path("/pendencias-baixas/{idbam}")
    public Response listarPendenciasBaixas(
            @PathParam("idbam") int idbam,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        pendenciaBaixaService.validarAcessoBam(idbam, unidadeLogada);
        return Response.ok(pendenciaBaixaService.listarPorBam(idbam).stream().map(pendenciaBaixaService::toMap).toList()).build();
    }

    @POST
    @Path("/pendencias-baixas")
    public Response registrarPendenciaBaixa(
            Map<String, Object> dto,
            @HeaderParam("X-GHBAM-UNIT") String unidadeLogada) {
        Map<String, Object> baixa = pendenciaBaixaService.toMap(pendenciaBaixaService.registrar(dto, unidadeLogada));
        jornadaPaService.limparCacheConformidade();
        return Response.ok(baixa).build();
    }

    private LocalDate parseData(String valor, LocalDate padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        String texto = valor.trim();
        // Aceita dia/mes sem zero ("2026-10-1") e o formato brasileiro; data invalida e
        // erro do filtro (400), nao falha do servidor (500).
        for (java.time.format.DateTimeFormatter formato : java.util.List.of(
                java.time.format.DateTimeFormatter.ofPattern("uuuu-M-d"),
                java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d-M-uuuu"))) {
            try {
                LocalDate data = LocalDate.parse(texto, formato);
                // "uuuu" aceita 5 digitos: ano 20261 viraria consulta de seculos.
                if (data.getYear() < 2000 || data.getYear() > LocalDate.now().getYear() + 1) break;
                return data;
            } catch (java.time.format.DateTimeParseException ignored) {
                // Tenta o proximo formato.
            }
        }
        throw new jakarta.ws.rs.BadRequestException("Data invalida no filtro: " + texto);
    }

    private AuthenticatedUser usuarioAtual() {
        Object usuario = requestContext.getProperty("ghbam.user");
        if (!(usuario instanceof AuthenticatedUser autenticado)
                || autenticado.matricula() == null
                || autenticado.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return autenticado;
    }

    private Response erro(Response.Status status, String mensagem) {
        return Response.status(status)
                .entity(Map.of("erro", mensagem == null ? "" : mensagem))
                .build();
    }
}
