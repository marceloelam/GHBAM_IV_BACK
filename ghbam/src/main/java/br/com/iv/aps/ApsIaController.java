package br.com.iv.aps;

import static br.com.iv.aps.ApsHttp.ACS;
import static br.com.iv.aps.ApsHttp.APS;
import static br.com.iv.aps.ApsHttp.GESTAO;
import static br.com.iv.aps.ApsHttp.VACINA;
import static br.com.iv.aps.ApsHttp.executar;
import static br.com.iv.aps.ApsHttp.pode;
import static br.com.iv.aps.ApsHttp.proibido;

import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Apoio de IA do G-PEC: explicar rejeicao do Centralizador e sugerir CIAP-2/CID-10 no SOAP. */
@Path("api/aps/ia")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// Sem unidade usa a da sessao (ApsHttp.unidade): unidade vazia nao e' "todas".
@br.com.iv.security.UnidadePadraoSessao
public class ApsIaController {

    @Inject
    SecurityPermissionService sps;
    @Inject
    ApsIaService ia;

    @Context
    ContainerRequestContext ctx;

    private AuthenticatedUser u() {
        return ApsHttp.usuario(ctx, sps);
    }

    @GET
    @Path("/status")
    public Response status() {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(ia::status);
    }

    @GET
    @Path("/fichas/{id}/explicar")
    public Response explicar(@PathParam("id") Long id) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS, VACINA, GESTAO)) {
            return proibido("Registros LEDI");
        }
        return executar(() -> ia.explicarRejeicao(id, ApsHttp.unidade(ctx, user, null)));
    }

    @POST
    @Path("/fichas/{id}/atualizar-cadastro")
    public Response atualizarComCadastro(@PathParam("id") Long id) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, GESTAO, ACS)) {
            return proibido("Registros LEDI");
        }
        return executar(() -> ia.atualizarComCadastro(id, ApsHttp.unidade(ctx, user, null), user.matricula()));
    }

    @POST
    @Path("/fichas/{id}/buscar-cadsus")
    public Response buscarNoCadsus(@PathParam("id") Long id) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, GESTAO, ACS)) {
            return proibido("Registros LEDI");
        }
        return executar(() -> ia.buscarNoCadsus(id, ApsHttp.unidade(ctx, user, null)));
    }

    @POST
    @Path("/sugerir-codigos")
    public Response sugerirCodigos(Map<String, Object> body) {
        if (!pode(u(), APS)) {
            return proibido("Atendimento");
        }
        return executar(() -> ia.sugerirCodigos(body == null ? Map.of() : body));
    }
}
