package br.com.iv.controller;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.TransmitirRemessaDto;
import br.com.iv.entity.FaturamentoRemessa;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoRemessaService;

@Path("faturamento/remessas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoRemessaController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoRemessaService remessaService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    public List<FaturamentoRemessa> listar(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return remessaService.listar(idcompetencia, unidadeDaRemessa(u, unidade));
    }

    @GET
    @Path("arquivo/{id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response arquivo(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        try {
            byte[] conteudo = remessaService.arquivo(id, unidadeDaRemessa(u, null));
            return Response.ok(conteudo)
                    .header("Content-Disposition", "attachment; filename=\"remessa-" + id + ".txt\"")
                    .build();
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("transmitir/{id}")
    public Response transmitir(@PathParam("id") int id, TransmitirRemessaDto dto) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("bpaexportar", "kadm"))) {
            throw new ForbiddenException();
        }
        if (dto == null || dto.getProtocolo() == null || dto.getProtocolo().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Informe o protocolo de transmissao.").build();
        }
        try {
            return Response.ok(remessaService.registrarTransmissao(id, dto.getProtocolo(), dto.getObservacao(), u,
                    unidadeDaRemessa(u, null))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    // Cancelar tem a mesma permissao de transmitir: as duas acoes decidem o que vale como
    // remessa oficial do mes. O cancelamento devolve a producao para a fila de exportacao,
    // entao nao e uma acao de leitura disfarcada.
    @POST
    @Path("cancelar/{id}")
    public Response cancelar(@PathParam("id") int id, CancelarRemessaDto dto) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("bpaexportar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(remessaService.cancelar(id, dto == null ? null : dto.motivo(), u,
                    unidadeDaRemessa(u, null))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    public record CancelarRemessaDto(String motivo) {
    }

    private String unidadeDaRemessa(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        if (unidadeSolicitada != null && !unidadeSolicitada.isBlank()) {
            return unidadeSolicitada;
        }
        return user.unidade();
    }
}
