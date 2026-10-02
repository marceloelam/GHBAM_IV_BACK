package br.com.iv.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.entity.LgpdSolicitacaoExclusao;
import br.com.iv.entity.Pacientes;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.LgpdSolicitacaoExclusaoService;
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

// Fluxo de solicitacao de exclusao de dados (LGPD art. 18, VI). Restrito a
// kadm (ver RoutePermissionCatalog "lgpd") - mesmo publico de
// "pacientes/exportar": o sistema nao tem login de paciente, entao quem
// recebe e registra o pedido (presencial/telefone/ouvidoria) e sempre um
// servidor administrativo, nunca o proprio paciente direto no sistema.
@Path("lgpd")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LgpdController {

    @Inject
    LgpdSolicitacaoExclusaoService service;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    public static class PedidoExclusaoRequest {
        public String nomeSolicitanteExterno;
        public String detalhePedido;
    }

    @POST
    @Path("exclusao/{idpaciente}")
    public Response solicitarExclusao(@PathParam("idpaciente") int idpaciente, PedidoExclusaoRequest body) {
        Pacientes paciente = Pacientes.<Pacientes>findByIdOptional(idpaciente).orElse(null);
        if (paciente == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"erro\":\"Paciente nao encontrado\"}")
                    .build();
        }
        AuthenticatedUser atendente = currentUser();
        if (atendente == null || atendente.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        LgpdSolicitacaoExclusao registro = service.registrarPedido(
                idpaciente, atendente,
                body == null ? null : body.nomeSolicitanteExterno,
                body == null ? null : body.detalhePedido);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("status", registro.getStatus());
        resposta.put("mensagemParaOSolicitante", registro.getBaseLegal());
        resposta.put("idsolicitacao", registro.getIdsolicitacao());
        resposta.put("registradoPor", atendente.nome());
        return Response.ok(resposta).build();
    }

    @GET
    @Path("exclusao/paciente/{idpaciente}")
    public Response historico(@PathParam("idpaciente") int idpaciente) {
        List<LgpdSolicitacaoExclusao> historico = service.historicoPorPaciente(idpaciente);
        return Response.ok(historico).build();
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : securityPermissionService.resolve(requestContext);
    }
}
