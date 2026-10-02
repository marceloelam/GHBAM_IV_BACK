package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.entity.CirurgiaChecklist;
import br.com.iv.entity.CirurgiaContagem;
import br.com.iv.entity.CirurgiaOpme;
import br.com.iv.entity.CirurgiaTempo;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.CirurgiaExecucaoService;

@Path("centro-cirurgico/execucao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CirurgiaExecucaoController {

    // Sincronizado com "centro-cirurgico/execucao" de RoutePermissionCatalog.java.
    private static final List<String> LEITURA = List.of("pcentrocirurgico", "kmedico", "kenfermagem",
            "pcirurgiaexecutar", "pcirurgiaopme", "kadm");
    private static final List<String> EXECUTAR = List.of("pcirurgiaexecutar", "pcentrocirurgico", "kadm");
    private static final List<String> OPME = List.of("pcirurgiaopme", "pcirurgiaexecutar", "pcentrocirurgico", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    CirurgiaExecucaoService execucaoService;

    private AuthenticatedUser user(List<String> permissoes) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(permissoes)) {
            throw new ForbiddenException();
        }
        return a;
    }

    private Response erro(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
    }

    @POST
    @Path("/{idcirurgia}/checklist")
    public Response salvarChecklist(@PathParam("idcirurgia") int idcirurgia, Map<String, Object> corpo) {
        AuthenticatedUser u = user(EXECUTAR);
        try {
            String momento = corpo == null ? null : String.valueOf(corpo.get("momento"));
            Object respostas = corpo == null ? null : corpo.get("respostasJson");
            boolean conforme = corpo != null && Boolean.TRUE.equals(corpo.get("conforme"));
            String pendencias = corpo == null || corpo.get("pendencias") == null
                    ? null
                    : String.valueOf(corpo.get("pendencias"));
            CirurgiaChecklist ck = execucaoService.salvarChecklist(idcirurgia, momento,
                    respostas == null ? null : String.valueOf(respostas), conforme, pendencias, u);
            return Response.ok(ck).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/{idcirurgia}/checklist")
    public Response listarChecklist(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(CirurgiaChecklist.list("idcirurgia", idcirurgia)).build();
    }

    @POST
    @Path("/{idcirurgia}/tempo/{marco}")
    public Response registrarMarco(@PathParam("idcirurgia") int idcirurgia, @PathParam("marco") String marco) {
        AuthenticatedUser u = user(EXECUTAR);
        try {
            return Response.ok(execucaoService.registrarMarco(idcirurgia, marco, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/{idcirurgia}/tempo")
    public Response tempos(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(CirurgiaTempo.find("idcirurgia", idcirurgia).firstResult()).build();
    }

    @POST
    @Path("/{idcirurgia}/contagem")
    public Response registrarContagem(@PathParam("idcirurgia") int idcirurgia, CirurgiaContagem dados) {
        AuthenticatedUser u = user(EXECUTAR);
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(execucaoService.registrarContagem(idcirurgia, dados, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/{idcirurgia}/contagem")
    public Response listarContagem(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(CirurgiaContagem.list("idcirurgia", idcirurgia)).build();
    }

    @POST
    @Path("/{idcirurgia}/opme")
    public Response registrarOpme(@PathParam("idcirurgia") int idcirurgia, CirurgiaOpme dados) {
        AuthenticatedUser u = user(OPME);
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(execucaoService.registrarOpme(idcirurgia, dados, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/{idcirurgia}/opme")
    public Response listarOpme(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(CirurgiaOpme.list("idcirurgia", idcirurgia)).build();
    }

    @DELETE
    @Path("/{idcirurgia}/opme/{idopme}")
    public Response removerOpme(@PathParam("idcirurgia") int idcirurgia, @PathParam("idopme") int idopme) {
        user(OPME);
        try {
            execucaoService.removerOpme(idcirurgia, idopme);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/{idcirurgia}/pendencias")
    public Response pendencias(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        return Response.ok(execucaoService.pendenciasParaEncerrar(idcirurgia)).build();
    }

    /** Corpo: {"idprocedimento": true|false}. Id omitido mantem o valor atual. */
    @POST
    @Path("/{idcirurgia}/procedimentos/realizados")
    public Response confirmarRealizados(@PathParam("idcirurgia") int idcirurgia, Map<String, Boolean> corpo) {
        AuthenticatedUser u = user(EXECUTAR);
        try {
            return Response.ok(execucaoService.confirmarRealizados(idcirurgia, corpo, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @POST
    @Path("/{idcirurgia}/encerrar")
    public Response encerrar(@PathParam("idcirurgia") int idcirurgia) {
        AuthenticatedUser u = user(EXECUTAR);
        try {
            return Response.ok(execucaoService.encerrar(idcirurgia, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    /** Regera a producao de cirurgia ja encerrada cujo envio ao faturamento falhou. */
    @POST
    @Path("/{idcirurgia}/producao")
    public Response reprocessarProducao(@PathParam("idcirurgia") int idcirurgia) {
        AuthenticatedUser u = user(EXECUTAR);
        try {
            return Response.ok(execucaoService.reprocessarProducao(idcirurgia, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }
}
