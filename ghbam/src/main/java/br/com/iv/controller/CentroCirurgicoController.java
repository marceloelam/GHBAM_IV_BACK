package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
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

import br.com.iv.dto.CirurgiaAgendamentoDto;
import br.com.iv.dto.CirurgiaSuspensaoDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.CentroCirurgicoService;

@Path("centro-cirurgico")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CentroCirurgicoController {

    // Sincronizado com a entrada "centro-cirurgico" (raiz) de RoutePermissionCatalog.java,
    // que ja libera as permissoes de escrita do modulo tambem para leitura.
    private static final List<String> LEITURA = List.of("pcentrocirurgico", "kmedico", "kenfermagem",
            "pcirurgiaagendar", "pcirurgiasuspender", "kadm");
    private static final List<String> AGENDAR = List.of("pcirurgiaagendar", "pcentrocirurgico", "kadm");
    private static final List<String> SUSPENDER = List.of("pcirurgiasuspender", "pcentrocirurgico", "kadm");

    @Context
    ContainerRequestContext context;

    @Inject
    CentroCirurgicoService centroCirurgicoService;

    private AuthenticatedUser user(List<String> permissoes) {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(permissoes)) {
            throw new ForbiddenException();
        }
        return a;
    }

    private String unidadeDoUsuario(AuthenticatedUser u, String solicitada) {
        String doUsuario = u == null || u.unidade() == null ? "" : u.unidade().trim();
        if (!doUsuario.isBlank() && !"TODAS".equalsIgnoreCase(doUsuario)) {
            return doUsuario;
        }
        return solicitada == null || solicitada.isBlank() ? null : solicitada.trim();
    }

    private Response erro(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
    }

    @GET
    @Path("/mapa")
    public Response mapa(@QueryParam("data") String data, @QueryParam("unidade") String unidade,
            @QueryParam("idsala") Integer idsala) {
        AuthenticatedUser u = user(LEITURA);
        try {
            return Response.ok(centroCirurgicoService.mapaCirurgico(unidadeDoUsuario(u, unidade), data, idsala)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    public Response listar(@QueryParam("unidade") String unidade, @QueryParam("status") String status,
            @QueryParam("especialidade") String especialidade, @QueryParam("q") String busca,
            @QueryParam("pagina") Integer pagina, @QueryParam("tamanho") Integer tamanho) {
        AuthenticatedUser u = user(LEITURA);
        int pag = pagina == null ? 0 : Math.max(pagina, 0);
        int tam = tamanho == null ? 50 : tamanho;
        return Response.ok(centroCirurgicoService.listar(unidadeDoUsuario(u, unidade), status, especialidade, busca,
                pag, tam)).build();
    }

    @GET
    @Path("/{idcirurgia}")
    public Response detalhe(@PathParam("idcirurgia") int idcirurgia) {
        user(LEITURA);
        try {
            return Response.ok(centroCirurgicoService.detalhe(idcirurgia)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @GET
    @Path("/paciente/{idpaciente}")
    public Response porPaciente(@PathParam("idpaciente") int idpaciente) {
        user(LEITURA);
        return Response.ok(centroCirurgicoService.porPaciente(idpaciente)).build();
    }

    @GET
    @Path("/indicadores")
    public Response indicadores(@QueryParam("unidade") String unidade, @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim) {
        AuthenticatedUser u = user(LEITURA);
        try {
            return Response.ok(centroCirurgicoService.indicadores(unidadeDoUsuario(u, unidade), inicio, fim)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @POST
    public Response criar(CirurgiaAgendamentoDto dto) {
        AuthenticatedUser u = user(AGENDAR);
        try {
            if (dto != null && (dto.getUnidade() == null || dto.getUnidade().isBlank())) {
                dto.setUnidade(unidadeDoUsuario(u, null));
            }
            return Response.status(Response.Status.CREATED)
                    .entity(centroCirurgicoService.criar(dto, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @PUT
    @Path("/{idcirurgia}")
    public Response atualizar(@PathParam("idcirurgia") int idcirurgia, CirurgiaAgendamentoDto dto) {
        AuthenticatedUser u = user(AGENDAR);
        try {
            return Response.ok(centroCirurgicoService.atualizar(idcirurgia, dto, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @POST
    @Path("/{idcirurgia}/confirmar")
    public Response confirmar(@PathParam("idcirurgia") int idcirurgia) {
        AuthenticatedUser u = user(AGENDAR);
        try {
            return Response.ok(centroCirurgicoService.confirmar(idcirurgia, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @POST
    @Path("/{idcirurgia}/suspender")
    public Response suspender(@PathParam("idcirurgia") int idcirurgia, CirurgiaSuspensaoDto dto) {
        AuthenticatedUser u = user(SUSPENDER);
        try {
            return Response.ok(centroCirurgicoService.suspender(idcirurgia, dto, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }

    @DELETE
    @Path("/{idcirurgia}")
    public Response cancelar(@PathParam("idcirurgia") int idcirurgia, @QueryParam("motivo") String motivo) {
        AuthenticatedUser u = user(SUSPENDER);
        try {
            return Response.ok(centroCirurgicoService.cancelar(idcirurgia, motivo, u)).build();
        } catch (IllegalArgumentException e) {
            return erro(e);
        }
    }
}
