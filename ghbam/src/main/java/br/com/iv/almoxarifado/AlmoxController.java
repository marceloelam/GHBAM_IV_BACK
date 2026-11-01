package br.com.iv.almoxarifado;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Almoxarifado. "almoxarifado/setor/*" (pedir material e ver os proprios pedidos)
 * exige palmoxpedir, palmoxarifado ou kadm; o resto exige palmoxarifado ou kadm
 * (RoutePermissionCatalog), checado aqui tambem.
 */
@Path("almoxarifado")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AlmoxController {

    private static final List<String> GESTAO = List.of("palmoxarifado", "kadm");
    private static final List<String> PEDIR = List.of("palmoxpedir", "palmoxarifado", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    AlmoxService service;

    @Inject
    AlmoxGestaoService gestao;

    private AuthenticatedUser logado() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || a.matricula() == null || a.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return a;
    }

    private AuthenticatedUser solicitante() {
        AuthenticatedUser a = logado();
        if (!a.hasAnyPermission(PEDIR)) throw new ForbiddenException();
        return a;
    }

    private AuthenticatedUser gestor() {
        AuthenticatedUser a = logado();
        if (!a.hasAnyPermission(GESTAO)) throw new ForbiddenException();
        return a;
    }

    private String unidade(AuthenticatedUser u, String solicitada) {
        String doUsuario = u.unidade() == null ? "" : u.unidade().trim();
        String escolhida = !doUsuario.isBlank() && !"TODAS".equalsIgnoreCase(doUsuario) ? doUsuario
                : solicitada == null ? "" : solicitada.trim();
        if (escolhida.isBlank() || "TODAS".equalsIgnoreCase(escolhida)) {
            throw new BadRequestException("Informe a unidade do almoxarifado.");
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

    // ---- Setor (permissao Pedir material) ----

    @GET
    @Path("setor/catalogo")
    public Response catalogo(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = solicitante();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarItens(un, true).stream()
                .map(i -> Map.of("iditem", i.iditem, "descricao", i.descricao, "unidadeMedida", i.unidadeMedida, "grupo", i.grupo))
                .toList());
    }

    @GET
    @Path("setor/setores")
    public Response setores(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = solicitante();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.setoresDaUnidade(un));
    }

    /** Pedidos do setor escolhido; sem setor, nada (um setor nao ve o pedido do outro). */
    @GET
    @Path("setor/pedidos")
    public Response meusPedidos(@QueryParam("unidade") String unidade, @QueryParam("setor") String setor) {
        AuthenticatedUser u = solicitante();
        String un = unidade(u, unidade);
        if (setor == null || setor.isBlank()) return Response.ok(List.of()).build();
        return executar(Response.Status.OK, () -> service.requisicoesDoSetor(un, setor, u.matricula()));
    }

    @POST
    @Path("setor/pedidos")
    public Response pedir(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = solicitante();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.criarRequisicao(un, payload, u));
    }

    @POST
    @Path("setor/pedidos/{id}/cancelar")
    public Response cancelarPedido(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = solicitante();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.cancelar(un, id, u.matricula()));
    }

    // ---- Gestao do almoxarifado ----

    @GET
    @Path("itens")
    public Response itens(@QueryParam("unidade") String unidade, @QueryParam("todos") boolean todos) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarItens(un, !todos));
    }

    @POST
    @Path("itens")
    public Response criarItem(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.salvarItem(un, null, payload, u.matricula()));
    }

    @PUT
    @Path("itens/{id}")
    public Response alterarItem(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.salvarItem(un, id, payload, u.matricula()));
    }

    // ---- Setores que pedem material (lista propria do almoxarifado) ----

    @GET
    @Path("setores")
    public Response listarSetores(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarSetores(un));
    }

    @POST
    @Path("setores")
    public Response incluirSetor(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.incluirSetor(un, payload, u.matricula()));
    }

    @POST
    @Path("setores/comuns")
    public Response incluirSetoresComuns(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.incluirSetoresComuns(un, u.matricula()));
    }

    @POST
    @Path("setores/{id}/remover")
    public Response removerSetor(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.removerSetor(un, id));
    }

    @GET
    @Path("alertas")
    public Response alertas(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.abaixoDoMinimo(un));
    }

    @POST
    @Path("entradas")
    public Response entrada(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.entrada(un, payload, u.matricula()));
    }

    @POST
    @Path("ajustes")
    public Response ajuste(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> service.ajustar(un, payload, u.matricula()));
    }

    @GET
    @Path("movimentacoes")
    public Response movimentacoes(@QueryParam("unidade") String unidade, @QueryParam("iditem") Long iditem) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.movimentacoes(un, iditem));
    }

    @GET
    @Path("requisicoes")
    public Response requisicoes(@QueryParam("unidade") String unidade, @QueryParam("status") String status) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.listarRequisicoes(un, status));
    }

    @POST
    @Path("requisicoes/{id}/atender")
    public Response atender(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.atender(un, id, payload, u.matricula()));
    }

    @GET
    @Path("curva-abc")
    public Response curvaAbc(@QueryParam("unidade") String unidade, @QueryParam("dias") Integer dias) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> service.curvaAbc(un, dias == null ? 90 : dias));
    }

    // ---- Fornecedores, compras e patrimonio (gestao) ----

    @GET
    @Path("fornecedores")
    public Response fornecedores(@QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.listarFornecedores(un));
    }

    @POST
    @Path("fornecedores")
    public Response criarFornecedor(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> gestao.salvarFornecedor(un, null, payload, u.matricula()));
    }

    @PUT
    @Path("fornecedores/{id}")
    public Response alterarFornecedor(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.salvarFornecedor(un, id, payload, u.matricula()));
    }

    @GET
    @Path("compras")
    public Response compras(@QueryParam("unidade") String unidade, @QueryParam("status") String status) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.listarCompras(un, status));
    }

    @POST
    @Path("compras")
    public Response criarCompra(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> gestao.criarCompra(un, payload, u.matricula()));
    }

    @POST
    @Path("compras/ponto-de-pedido")
    public Response gerarCompra(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> gestao.gerarCompraDoPontoDePedido(un, payload, u.matricula()));
    }

    @POST
    @Path("compras/{id}/enviar")
    public Response enviarCompra(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.enviarCompra(un, id));
    }

    @POST
    @Path("compras/{id}/cancelar")
    public Response cancelarCompra(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.cancelarCompra(un, id));
    }

    @POST
    @Path("compras/{id}/receber")
    public Response receberCompra(@PathParam("id") Long id, @QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.receberCompra(un, id, payload, u.matricula()));
    }

    @GET
    @Path("patrimonio")
    public Response patrimonio(@QueryParam("unidade") String unidade, @QueryParam("status") String status) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.listarPatrimonio(un, status));
    }

    @POST
    @Path("patrimonio")
    public Response cadastrarBem(@QueryParam("unidade") String unidade, Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.CREATED, () -> gestao.cadastrarBem(un, payload, u.matricula()));
    }

    @GET
    @Path("patrimonio/{id}")
    public Response historicoBem(@PathParam("id") Long id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.historicoBem(un, id));
    }

    @POST
    @Path("patrimonio/{id}/{acao}")
    public Response acaoBem(@PathParam("id") Long id, @PathParam("acao") String acao, @QueryParam("unidade") String unidade,
            Map<String, Object> payload) {
        AuthenticatedUser u = gestor();
        String un = unidade(u, unidade);
        return executar(Response.Status.OK, () -> gestao.acaoBem(un, id, acao, payload, u.matricula()));
    }
}
