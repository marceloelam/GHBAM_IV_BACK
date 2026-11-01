package br.com.iv.ponto;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Gestao do ponto (RH): resumo e banco de horas, espelho, ajuste, anulacao e
 * exportacao para a folha. Exige krh, pcontroleplantoes ou kadm (catalogo "rh/ponto").
 * "rh/ponto/meu" e o espelho do proprio servidor (qualquer usuario logado).
 */
@Path("rh/ponto")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PontoController {

    private static final List<String> GESTAO = List.of("krh", "pcontroleplantoes", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    PontoService service;

    private AuthenticatedUser logado() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || a.matricula() == null || a.matricula().isBlank()) throw new NotAuthorizedException("Bearer");
        return a;
    }

    private AuthenticatedUser gestor() {
        AuthenticatedUser a = logado();
        if (!a.hasAnyPermission(GESTAO)) throw new ForbiddenException();
        return a;
    }

    private String unidade(AuthenticatedUser u, String solicitada) {
        String doUsuario = u.unidade() == null ? "" : u.unidade().trim();
        String escolhida = !doUsuario.isBlank() && !"TODAS".equalsIgnoreCase(doUsuario) ? doUsuario : solicitada == null ? "" : solicitada.trim();
        if (escolhida.isBlank() || "TODAS".equalsIgnoreCase(escolhida)) throw new BadRequestException("Informe a unidade.");
        return escolhida.toUpperCase();
    }

    private static YearMonth mes(String valor) {
        try {
            return valor == null || valor.isBlank() ? YearMonth.now() : YearMonth.parse(valor.trim());
        } catch (RuntimeException e) {
            throw new BadRequestException("Mes invalido (use aaaa-MM).");
        }
    }

    private Response executar(Supplier<Object> acao) {
        try {
            return Response.ok(acao.get()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @GET
    @Path("resumo")
    public Response resumo(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes,
            @QueryParam("inicio") String inicio, @QueryParam("fim") String fim) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        LocalDate[] p = periodo(inicio, fim);
        if (p != null) return executar(() -> service.resumo(un, YearMonth.from(p[1]), p[0], p[1]));
        YearMonth m = mes(mes);
        return executar(() -> service.resumo(un, m));
    }

    @GET
    @Path("espelho")
    public Response espelho(@QueryParam("unidade") String unidade, @QueryParam("matricula") String matricula, @QueryParam("mes") String mes,
            @QueryParam("inicio") String inicio, @QueryParam("fim") String fim) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        if (matricula == null || matricula.isBlank()) throw new BadRequestException("Informe a matricula.");
        LocalDate[] p = periodo(inicio, fim);
        if (p != null) return executar(() -> service.espelho(un, matricula.trim(), YearMonth.from(p[1]), p[0], p[1]));
        YearMonth m = mes(mes);
        return executar(() -> service.espelho(un, matricula.trim(), m));
    }

    /** Periodo livre de/ate (aaaa-MM-dd, inclusivo, no maximo 1 ano); null quando nao informado. */
    private static LocalDate[] periodo(String inicio, String fim) {
        if (inicio == null || inicio.isBlank() || fim == null || fim.isBlank()) return null;
        LocalDate de;
        LocalDate ate;
        try {
            de = LocalDate.parse(inicio.trim());
            ate = LocalDate.parse(fim.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadRequestException("Periodo invalido (use aaaa-MM-dd).");
        }
        if (ate.isBefore(de)) throw new BadRequestException("A data final e anterior a inicial.");
        if (de.plusYears(1).isBefore(ate)) throw new BadRequestException("Periodo maximo de 1 ano.");
        return new LocalDate[] { de, ate };
    }

    @GET
    @Path("meu")
    public Response meuEspelho(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes) {
        AuthenticatedUser u = logado();
        String un = unidade(u, unidade);
        YearMonth m = mes(mes);
        return executar(() -> service.espelho(un, u.matricula(), m));
    }

    @POST
    @Path("ajuste")
    public Response ajuste(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(() -> service.ajustar(un, payload, u.matricula()));
    }

    @POST
    @Path("{id}/anular")
    public Response anular(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        Object motivo = payload == null ? null : payload.get("motivo");
        return executar(() -> service.anular(un, id, motivo == null ? null : String.valueOf(motivo), u.matricula()));
    }

    /** Folhas de ponto de todos (apuracao diaria de cada servidor) para impressao em lote. */
    @GET
    @Path("folhas")
    public Response folhas(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes,
            @QueryParam("inicio") String inicio, @QueryParam("fim") String fim) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        LocalDate[] p = periodo(inicio, fim);
        YearMonth m = p != null ? YearMonth.from(p[1]) : mes(mes);
        LocalDate de = p != null ? p[0] : m.atDay(1);
        LocalDate ate = p != null ? p[1] : m.atEndOfMonth();
        return executar(() -> service.resumo(un, m, de, ate, true));
    }

    // ---- Ocorrencias ----

    @GET
    @Path("ocorrencias/tipos")
    public Response tiposOcorrencia() {
        gestor();
        return Response.ok(service.tiposOcorrencia()).build();
    }

    @GET
    @Path("ocorrencias")
    public Response ocorrencias(@QueryParam("unidade") String unidade, @QueryParam("matricula") String matricula,
            @QueryParam("mes") String mes, @QueryParam("inicio") String inicio, @QueryParam("fim") String fim) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        LocalDate[] p = periodo(inicio, fim);
        YearMonth m = mes(mes);
        LocalDate de = p != null ? p[0] : m.atDay(1);
        LocalDate ate = p != null ? p[1] : m.atEndOfMonth();
        return executar(() -> service.ocorrencias(un, matricula, de, ate));
    }

    @POST
    @Path("ocorrencia")
    public Response registrarOcorrencia(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(() -> service.registrarOcorrencia(un, payload, u.matricula()));
    }

    @POST
    @Path("ocorrencia/{id}/anular")
    public Response anularOcorrencia(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        Object motivo = payload == null ? null : payload.get("motivo");
        return executar(() -> service.anularOcorrencia(un, id, motivo == null ? null : String.valueOf(motivo), u.matricula()));
    }

    @GET
    @Path("ferias")
    public Response ferias(@QueryParam("unidade") String unidade, @QueryParam("ano") Integer ano) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        int a = ano == null ? LocalDate.now().getYear() : ano;
        return executar(() -> service.controleFerias(un, a));
    }

    // ---- Feriados ----

    @GET
    @Path("feriados")
    public Response feriados(@QueryParam("unidade") String unidade, @QueryParam("ano") Integer ano) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        int a = ano == null ? LocalDate.now().getYear() : ano;
        return executar(() -> service.listarFeriados(un, a));
    }

    @POST
    @Path("feriado")
    public Response registrarFeriado(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(() -> service.registrarFeriado(un, payload, u.matricula()));
    }

    @POST
    @Path("feriado/{id}/excluir")
    public Response excluirFeriado(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(() -> service.excluirFeriado(un, id));
    }

    // ---- Fechamento do mes ----

    @GET
    @Path("fechamento")
    public Response fechamento(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        YearMonth m = mes(mes);
        return executar(() -> service.situacaoFechamento(un, m));
    }

    @POST
    @Path("fechamento")
    public Response fechar(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        YearMonth m = mes(mes);
        return executar(() -> service.fecharMes(un, m, u.matricula()));
    }

    /** Reabrir mes ja enviado a folha e decisao do RH (krh/kadm), nao da chefia da escala. */
    @POST
    @Path("fechamento/reabrir")
    public Response reabrir(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        if (!u.hasAnyPermission(List.of("krh", "kadm"))) throw new ForbiddenException();
        String un = unidade(u, unidade);
        YearMonth m = mes(mes);
        Object motivo = payload == null ? null : payload.get("motivo");
        return executar(() -> service.reabrirMes(un, m, motivo == null ? null : String.valueOf(motivo), u.matricula()));
    }

    @GET
    @Path("exportar")
    @Produces("text/csv")
    public Response exportar(@QueryParam("unidade") String unidade, @QueryParam("mes") String mes) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        YearMonth m = mes(mes);
        return Response.ok("﻿" + service.exportarCsv(un, m))
                .header("Content-Disposition", "attachment; filename=ponto-" + un + "-" + m + ".csv").build();
    }
}
