package br.com.iv.protocolo;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
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

/**
 * Protocolos em fluxograma aplicados ao paciente. Profissional, matricula e unidade vem da
 * sessao, nunca do cliente. Gravar exige ghbam.protocolos.gravar=true; consultar funciona
 * sempre, para o historico continuar visivel se a chave for desligada depois.
 */
@Path("protocolos-fluxograma/execucoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProtocoloExecucaoController {

    // pprotocoloaprovacao: enfermagem/multi da comissao precisam abrir a tela para aprovar.
    static final List<String> ACESSO = List.of("kmedico", "kadm", "pprotocoloaprovacao");

    @Context
    ContainerRequestContext request;

    @Inject
    SecurityPermissionService security;

    @Inject
    ProtocoloExecucaoService service;

    private AuthenticatedUser usuario() {
        AuthenticatedUser user = security.resolve(request);
        if (user == null || user.matricula() == null || user.matricula().isBlank()) throw new NotAuthorizedException("Bearer");
        if (!user.hasAnyPermission(ACESSO)) throw new ForbiddenException();
        return user;
    }

    /** Unidade da sessao; quem loga como TODAS nao registra nem consulta protocolo de paciente. */
    private String unidade(AuthenticatedUser user) {
        String sessao = security.unidadeDaSessao(request);
        if (sessao == null || sessao.isBlank()) sessao = user.unidade();
        if (sessao == null || sessao.isBlank() || "TODAS".equalsIgnoreCase(sessao.trim())) {
            throw new BadRequestException("Entre em uma unidade específica para registrar protocolos.");
        }
        return sessao.trim().split(":")[0];
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).entity(Map.of("erro", mensagem)).build();
    }

    private static String texto(Map<String, Object> corpo, String chave) {
        Object v = corpo == null ? null : corpo.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    @GET
    @Path("config")
    public Map<String, Object> config() {
        usuario();
        return Map.of("gravacaoAtiva", service.gravacaoAtiva());
    }

    @GET
    public List<Map<String, Object>> listar(@QueryParam("idpaciente") String idpaciente, @QueryParam("bam") String bam) {
        AuthenticatedUser user = usuario();
        return service.listar(idpaciente, bam, unidade(user));
    }

    @POST
    public Response iniciar(Map<String, Object> corpo) {
        AuthenticatedUser user = usuario();
        if (!service.gravacaoAtiva()) return erro(Response.Status.CONFLICT, "Gravação de protocolos desligada.");
        String protocoloId = texto(corpo, "protocoloId");
        String versao = texto(corpo, "versao");
        String titulo = texto(corpo, "titulo");
        String idpaciente = texto(corpo, "idpaciente");
        if (ProtocoloExecucaoService.limitar(protocoloId, 80) == null || ProtocoloExecucaoService.limitar(versao, 20) == null
                || ProtocoloExecucaoService.limitar(titulo, 200) == null || ProtocoloExecucaoService.limitar(idpaciente, 40) == null) {
            return erro(Response.Status.BAD_REQUEST, "Informe protocolo, versão e paciente.");
        }
        long id = service.iniciar(protocoloId, versao, titulo, idpaciente, texto(corpo, "bam"),
                unidade(user), user.matricula().trim(), user.nome(), ProtocoloExecucaoService.registroProfissional(user));
        return Response.status(Response.Status.CREATED).entity(Map.of("id", id)).build();
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") long id, Map<String, Object> corpo) {
        AuthenticatedUser user = usuario();
        if (!service.gravacaoAtiva()) return erro(Response.Status.CONFLICT, "Gravação de protocolos desligada.");
        try {
            boolean ok = service.atualizar(id, user.matricula().trim(), unidade(user), corpo == null ? null : corpo.get("passos"),
                    texto(corpo, "status"), texto(corpo, "desfecho"), texto(corpo, "justificativa"), texto(corpo, "resumo"));
            if (!ok) return erro(Response.Status.CONFLICT, "Execução já encerrada ou de outro profissional.");
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }
}
