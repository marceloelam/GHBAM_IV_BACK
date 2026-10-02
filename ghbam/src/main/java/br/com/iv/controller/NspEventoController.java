package br.com.iv.controller;

import br.com.iv.dto.NspAcaoDto;
import br.com.iv.dto.NspEventoDto;
import br.com.iv.entity.NspEvento;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.NspEventoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Nucleo de Seguranca do Paciente: notificacao de incidente, analise e plano de acao.
 * Acesso restrito de proposito: nenhum perfil clinico (medico, enfermagem etc.) nem
 * kadm dao acesso automatico. pnsp ou pnspgestao notificam e acompanham a fila da
 * unidade; pnspgestao tambem ve o notificante; classificar, investigar, montar plano
 * de acao e encerrar exige pnspclassificar.
 */
@Path("nsp/eventos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NspEventoController {

    private static final List<String> LEITURA = List.of("pnsp", "pnspgestao", "pnspclassificar");
    private static final List<String> PODE_CLASSIFICAR = List.of("pnspclassificar");
    private static final List<String> PODE_VER_NOTIFICANTE = List.of("pnspgestao");

    @Context
    ContainerRequestContext context;

    @Inject
    NspEventoService service;

    // ------------------------------------------------------------------ leitura

    @GET
    public Response listar(@QueryParam("unidade") String unidade,
            @QueryParam("status") String status,
            @QueryParam("classificacao") String classificacao,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim) {
        AuthenticatedUser usuario = exigir(LEITURA);
        return executar(() -> service.listar(unidadeDoUsuario(usuario, unidade), status, classificacao,
                dataHora(inicio, false), dataHora(fim, true), ehGestao(usuario)));
    }

    @GET
    @Path("opcoes")
    public Response opcoes() {
        exigir(LEITURA);
        return Response.ok(Map.of(
                "tipos", NspEventoService.TIPOS.stream().sorted().toList(),
                "classificacoes", NspEventoService.CLASSIFICACOES.stream().sorted().toList(),
                "grausDano", NspEventoService.GRAUS_DANO.stream().sorted().toList())).build();
    }

    @GET
    @Path("indicadores")
    public Response indicadores(@QueryParam("unidade") String unidade,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim) {
        AuthenticatedUser usuario = exigir(LEITURA);
        return executar(() -> service.indicadores(unidadeDoUsuario(usuario, unidade),
                dataHora(inicio, false), dataHora(fim, true)));
    }

    @GET
    @Path("{id}")
    public Response detalhar(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser usuario = exigir(PODE_CLASSIFICAR);
        return executar(() -> service.detalhar(id, unidadeDoUsuario(usuario, unidade), ehGestao(usuario)));
    }

    // ------------------------------------------------------------------ escrita

    @POST
    public Response notificar(NspEventoDto dto) {
        AuthenticatedUser usuario = exigir(LEITURA);
        if (dto == null) {
            return erro(Response.Status.BAD_REQUEST, "Informe os dados da notificacao.");
        }
        dto.setUnidade(unidadeDoUsuario(usuario, dto.getUnidade()));
        try {
            NspEvento evento = service.notificar(dto, usuario);
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of("idevento", evento.getIdevento(), "protocolo", evento.getProtocolo(),
                            "status", evento.getStatus(), "prazoAnalise", evento.getPrazoAnalise()))
                    .build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return erro(Response.Status.CONFLICT, e.getMessage());
        }
    }

    @PUT
    @Path("{id}/classificacao")
    public Response classificar(@PathParam("id") Long id, NspEventoDto dto) {
        AuthenticatedUser usuario = exigir(PODE_CLASSIFICAR);
        if (dto == null) {
            return erro(Response.Status.BAD_REQUEST, "Informe a classificacao do caso.");
        }
        return executar(() -> service.classificar(id, unidadeDoUsuario(usuario, dto.getUnidade()), dto, usuario));
    }

    @PATCH
    @Path("{id}/status")
    public Response atualizarStatus(@PathParam("id") Long id, NspEventoDto dto) {
        AuthenticatedUser usuario = exigir(PODE_CLASSIFICAR);
        if (dto == null) {
            return erro(Response.Status.BAD_REQUEST, "Informe a nova situacao do caso.");
        }
        return executar(() -> service.atualizarStatus(id, unidadeDoUsuario(usuario, dto.getUnidade()),
                dto.getStatus(), dto.getObservacao(), usuario));
    }

    @POST
    @Path("{id}/encerramento")
    public Response encerrar(@PathParam("id") Long id, NspEventoDto dto) {
        AuthenticatedUser usuario = exigir(PODE_CLASSIFICAR);
        if (dto == null) {
            return erro(Response.Status.BAD_REQUEST, "Informe a conclusao da analise.");
        }
        return executar(() -> service.encerrar(id, unidadeDoUsuario(usuario, dto.getUnidade()), dto, usuario));
    }

    @POST
    @Path("{id}/acoes")
    public Response adicionarAcao(@PathParam("id") Long id, @QueryParam("unidade") String unidade, NspAcaoDto dto) {
        AuthenticatedUser usuario = exigir(PODE_CLASSIFICAR);
        if (dto == null) {
            return erro(Response.Status.BAD_REQUEST, "Informe os dados da acao.");
        }
        return executar(() -> service.adicionarAcao(id, unidadeDoUsuario(usuario, unidade), dto, usuario));
    }

    @PATCH
    @Path("{id}/acoes/{idacao}/conclusao")
    public Response concluirAcao(@PathParam("id") Long id, @PathParam("idacao") Long idacao,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser usuario = exigir(PODE_CLASSIFICAR);
        return executar(() -> service.concluirAcao(id, idacao, unidadeDoUsuario(usuario, unidade), usuario));
    }

    // ------------------------------------------------------------------ apoio

    private AuthenticatedUser exigir(List<String> permissoes) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser usuario) || !usuario.hasAnyPermission(permissoes)) {
            throw new ForbiddenException();
        }
        if (bloqueado(usuario)) {
            throw new ForbiddenException();
        }
        return usuario;
    }

    /**
     * Bloqueio individual: tem precedencia sobre o perfil assistencial, que e o
     * que libera o modulo por padrao. A flag e lida direto do mapa porque
     * hasPermission responde true para qualquer permissao quando o usuario e
     * superusuario - o que bloquearia justamente quem administra o sistema.
     */
    private boolean bloqueado(AuthenticatedUser usuario) {
        return usuario.permissions() != null
                && Boolean.TRUE.equals(usuario.permissions().get("pnspbloqueado"));
    }

    private boolean ehGestao(AuthenticatedUser usuario) {
        return usuario != null && usuario.hasAnyPermission(PODE_VER_NOTIFICANTE);
    }

    /**
     * A unidade vem sempre do usuario logado. So quem opera como TODAS (sem unidade
     * propria) pode informar a sigla na requisicao - o resto ficaria livre para ler
     * e escrever caso de outra unidade.
     */
    private String unidadeDoUsuario(AuthenticatedUser usuario, String solicitada) {
        String doUsuario = usuario == null || usuario.unidade() == null ? "" : usuario.unidade().split(":", 2)[0].trim();
        if (!doUsuario.isEmpty() && !"TODAS".equalsIgnoreCase(doUsuario)) {
            return doUsuario;
        }
        return solicitada == null ? "" : solicitada.split(":", 2)[0].trim();
    }

    /** Aceita 2026-09-01 e 2026-09-01T14:30 na mesma query string. */
    private LocalDateTime dataHora(String valor, boolean fimDoDia) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        try {
            if (texto.length() <= 10) {
                LocalDate data = LocalDate.parse(texto);
                return fimDoDia ? data.atTime(23, 59, 59) : data.atStartOfDay();
            }
            return LocalDateTime.parse(texto);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Data invalida: " + texto);
        }
    }

    private Response executar(Acao acao) {
        try {
            return Response.ok(acao.executar()).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return erro(Response.Status.CONFLICT, e.getMessage());
        }
    }

    private Response erro(Response.Status status, String mensagem) {
        return Response.status(status)
                .entity(Map.of("erro", mensagem == null ? "Requisicao invalida." : mensagem)).build();
    }

    @FunctionalInterface
    private interface Acao {
        Object executar();
    }
}
