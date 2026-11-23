package br.com.iv.tutorial;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Log de tutoriais ("Como usar").
 * - POST registrar: qualquer usuario autenticado (AUTHENTICATED_ONLY_PATHS do RbacRequestFilter);
 *   matricula, nome e unidade vem do token, nunca do cliente.
 * - GET (listagem): mesmas permissoes do Log do sistema (RoutePermissionCatalog: kadm, plogs),
 *   sempre na unidade do token.
 */
@Path("api/tutorial-log")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TutorialLogController {

    static final int PERIODO_MAXIMO_DIAS = 92;

    @Context
    ContainerRequestContext context;

    @Inject
    TutorialLogService service;

    @Inject
    SecurityPermissionService security;

    /**
     * Unidade da sessao (claim do JWT, atualizada na troca de unidade). u.unidade() e a unidade
     * do cadastro do servidor: quem trocava de unidade continuava vendo e gravando a original.
     */
    private String unidadeSessao(AuthenticatedUser u) {
        String sessao = security.unidadeDaSessao(context).split(":")[0].trim();
        return sessao.isBlank() ? (u.unidade() == null ? "" : u.unidade().split(":")[0].trim()) : sessao;
    }

    private AuthenticatedUser usuario() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || a.matricula() == null || a.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return a;
    }

    private static Response invalido(String mensagem) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", mensagem)).build();
    }

    @POST
    @Path("registrar")
    public Response registrar(Map<String, String> corpo) {
        AuthenticatedUser u = usuario();
        String tutorial = TutorialLogService.limitar(corpo == null ? null : corpo.get("tutorial"), 200);
        String tela = TutorialLogService.limitar(corpo == null ? null : corpo.get("tela"), 300);
        if (tutorial == null) return invalido("Tutorial não informado.");
        if (!TutorialLogService.telaValida(tela)) return invalido("Tela inválida.");
        service.registrar(
                u.matricula().trim(),
                TutorialLogService.limitar(u.nome(), 200),
                TutorialLogService.limitar(unidadeSessao(u), 80),
                tutorial,
                tela);
        return Response.noContent().build();
    }

    @GET
    public Response listar(@QueryParam("de") String de, @QueryParam("ate") String ate) {
        String unidade = unidadeSessao(usuario());
        if (unidade.isBlank()) return invalido("Unidade não encontrada na sessão.");
        LocalDate inicio;
        LocalDate fim;
        try {
            fim = ate == null || ate.isBlank() ? LocalDate.now() : LocalDate.parse(ate);
            inicio = de == null || de.isBlank() ? fim.minusDays(6) : LocalDate.parse(de);
        } catch (DateTimeParseException e) {
            return invalido("Data inválida. Use aaaa-mm-dd.");
        }
        if (inicio.isAfter(fim)) return invalido("A data inicial é depois da final.");
        if (inicio.plusDays(PERIODO_MAXIMO_DIAS).isBefore(fim)) return invalido("Período máximo de " + PERIODO_MAXIMO_DIAS + " dias.");
        return Response.ok(service.listar(unidade, inicio, fim)).build();
    }
}
