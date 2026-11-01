package br.com.iv.cme;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rastreabilidade da CME. Mesmas permissoes do apoio "assistencial/cme"
 * (RoutePermissionCatalog "cme/rastreio"), checadas aqui tambem para nao
 * depender so do filtro global.
 */
@Path("cme/rastreio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CmeController {

    private static final List<String> PERMISSOES = List.of("pcme", "pcentrocirurgico", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    CmeService service;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(PERMISSOES)) {
            throw new ForbiddenException();
        }
        return a;
    }

    /** Unidade da sessao; so quem opera como TODAS (sem unidade propria) escolhe pela query. */
    private String unidade(AuthenticatedUser u, String solicitada) {
        String doUsuario = u.unidade() == null ? "" : u.unidade().trim();
        String escolhida = !doUsuario.isBlank() && !"TODAS".equalsIgnoreCase(doUsuario) ? doUsuario
                : solicitada == null ? "" : solicitada.trim();
        if (escolhida.isBlank() || "TODAS".equalsIgnoreCase(escolhida)) {
            throw new BadRequestException("Informe a unidade da CME.");
        }
        return escolhida.toUpperCase();
    }

    private Response executar(Response.Status sucesso, Supplier<Object> acao) {
        try {
            return Response.status(sucesso).entity(acao.get()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    private static String texto(Map<String, Object> payload, String chave) {
        Object v = payload == null ? null : payload.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    @GET
    @Path("ciclos")
    public Response listarCiclos(@QueryParam("unidade") String unidade, @QueryParam("status") String status,
            @QueryParam("dia") String dia) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarCiclos(un, status, dia));
    }

    @GET
    @Path("ciclos/{id}")
    public Response detalheCiclo(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.detalheCiclo(un, id));
    }

    @POST
    @Path("ciclos")
    public Response criarCiclo(@QueryParam("unidade") String unidade, CmeCiclo dados) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.criarCiclo(un, dados, u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/pacotes")
    public Response adicionarPacotes(@PathParam("id") Long id, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.adicionarPacotes(un, id, payload, u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/indicadores")
    public Response registrarIndicadores(@PathParam("id") Long id, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.registrarIndicadores(un, id, payload, u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/liberar")
    public Response liberar(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.liberar(un, id, u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/reprovar")
    public Response reprovar(@PathParam("id") Long id, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.reprovar(un, id, texto(payload, "motivo"), u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/reprocessar-carga")
    public Response reprocessarCarga(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.reprocessarCarga(un, id, u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/excluir")
    public Response excluir(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> {
            service.excluir(un, id, u.matricula());
            return Map.of("excluido", id);
        });
    }

    @POST
    @Path("ciclos/{id}/cancelar")
    public Response cancelar(@PathParam("id") Long id, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.cancelar(un, id, texto(payload, "motivo"), u.matricula()));
    }

    @POST
    @Path("ciclos/{id}/parametros")
    public Response alterarParametros(@PathParam("id") Long id, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.alterarParametros(un, id, payload, u.matricula()));
    }

    @GET
    @Path("pacotes")
    public Response listarPacotes(@QueryParam("unidade") String unidade, @QueryParam("status") String status) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarPacotes(un, status));
    }

    @GET
    @Path("pacotes/{codigo}")
    public Response rastrear(@PathParam("codigo") String codigo, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.rastrear(un, codigo));
    }

    @GET
    @Path("paciente/{idpaciente}")
    public Response pacotesDoPaciente(@PathParam("idpaciente") Integer idpaciente, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.pacotesDoPaciente(un, idpaciente));
    }

    @POST
    @Path("pacotes/{codigo}/distribuir")
    public Response distribuir(@PathParam("codigo") String codigo, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.distribuir(un, codigo, texto(payload, "setor"), u.matricula()));
    }

    @POST
    @Path("pacotes/{codigo}/utilizar")
    public Response utilizar(@PathParam("codigo") String codigo, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.utilizar(un, codigo, payload, u.matricula()));
    }

    @POST
    @Path("pacotes/{codigo}/reprocessar")
    public Response reprocessar(@PathParam("codigo") String codigo, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.reprocessar(un, codigo, u.matricula()));
    }

    @GET
    @Path("preparo")
    public Response listarPreparo(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarPreparo(un));
    }

    @POST
    @Path("preparo")
    public Response receber(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.receber(un, payload, u.matricula()));
    }

    @POST
    @Path("preparo/{id}/limpar")
    public Response limpar(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.limpar(un, id, payload, u.matricula()));
    }

    @POST
    @Path("preparo/{id}/preparar")
    public Response preparar(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.preparar(un, id, payload, u.matricula()));
    }

    @POST
    @Path("pacotes/{codigo}/baixar")
    public Response baixar(@PathParam("codigo") String codigo, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = user();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.descartar(un, codigo, texto(payload, "motivo"), u.matricula()));
    }
}
