package br.com.iv.controller;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.FluxoPacientesService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Quadro de fluxo de pacientes. Autorizacao e unidade conferidas aqui, sem depender do
 * enforce global do RBAC: perfis assistenciais, NIR, adm ou pfluxopacientes; a unidade e a
 * da sessao (o parametro so vale para quem loga como TODAS).
 */
@Path("fluxo-pacientes")
@Produces(MediaType.APPLICATION_JSON)
public class FluxoPacientesController {

    // kfaturamento e o perfil que abre o grupo NIR no menu.
    static final List<String> ACESSO = List.of("krecepcao", "ktriagem", "kmedico", "kenfermagem", "ktcenfermagem",
            "kfaturamento", "ppainelnir", "pfluxopacientes", "kadm");

    @Context
    ContainerRequestContext request;

    @Inject
    SecurityPermissionService security;

    @Inject
    FluxoPacientesService service;

    @GET
    public Response quadro(@QueryParam("unidade") String solicitada) {
        AuthenticatedUser user = security.resolve(request);
        if (user == null) throw new NotAuthorizedException("Bearer");
        if (!user.hasAnyPermission(ACESSO)) throw new ForbiddenException();
        return Response.ok(service.quadro(unidade(user, solicitada))).build();
    }

    private String unidade(AuthenticatedUser user, String solicitada) {
        String sessao = security.unidadeDaSessao(request);
        if (sessao == null || sessao.isBlank()) sessao = user.unidade();
        String escolhida = sessao != null && !sessao.isBlank() && !"TODAS".equalsIgnoreCase(sessao.trim()) ? sessao : solicitada;
        if (escolhida == null || escolhida.isBlank() || "TODAS".equalsIgnoreCase(escolhida.trim())) {
            throw new BadRequestException("Informe a unidade.");
        }
        return escolhida.trim().split(":")[0];
    }
}
