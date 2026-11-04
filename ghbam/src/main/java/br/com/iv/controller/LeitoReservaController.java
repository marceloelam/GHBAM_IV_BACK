package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.LeitoReservaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Reserva de leito pelo NIR. Permissao e unidade vem da sessao, nunca do cliente. */
@Path("leitoreserva")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LeitoReservaController {

    private static final List<String> NIR = List.of("pnir", "kfaturamento", "kadm");

    public record ReservaRequest(int idleito, int bam) {
    }

    public record CancelamentoRequest(String motivo) {
    }

    @Context
    ContainerRequestContext context;

    @Inject
    SecurityPermissionService security;

    @Inject
    LeitoReservaService service;

    @GET
    @Path("/ativas")
    public Response listarAtivas() {
        operador();
        return Response.ok(service.listarAtivas(unidade())).build();
    }

    @POST
    public Response reservar(ReservaRequest request) {
        AuthenticatedUser user = operador();
        Map<String, Object> reserva = service.reservar(request.idleito(), request.bam(), unidade(), user.matricula());
        return Response.status(Response.Status.CREATED).entity(reserva).build();
    }

    @PATCH
    @Path("/{id}/cancelamento")
    public Response cancelar(@PathParam("id") long id, CancelamentoRequest request) {
        AuthenticatedUser user = operador();
        service.cancelar(id, unidade(), request == null ? null : request.motivo(), user.matricula());
        return Response.noContent().build();
    }

    private AuthenticatedUser operador() {
        AuthenticatedUser user = security.resolve(context);
        if (user == null) {
            throw new NotAuthorizedException("Bearer");
        }
        if (!user.hasAnyPermission(NIR)) {
            throw new ForbiddenException();
        }
        return user;
    }

    private String unidade() {
        String sessao = security.unidadeDaSessao(context);
        String unidade = sessao == null || sessao.isBlank() ? security.resolve(context).unidade() : sessao;
        if (unidade == null || unidade.isBlank()) {
            throw new ForbiddenException("Sessão sem unidade.");
        }
        return unidade;
    }
}
