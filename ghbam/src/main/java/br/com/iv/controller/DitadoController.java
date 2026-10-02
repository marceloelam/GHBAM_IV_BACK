package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.TranscricaoAudioDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.TranscricaoAudioService;
import br.com.iv.service.TranscricaoAudioService.ResultadoConversaClinica;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("ditado")
@Produces(MediaType.APPLICATION_JSON)
public class DitadoController {

    @Inject
    TranscricaoAudioService transcricaoAudioService;

    @Context
    ContainerRequestContext requestContext;

    @POST
    @Path("/transcrever")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response transcrever(@RestForm("audio") FileUpload audio) {
        exigirAcessoClinico();
        try {
            String texto = transcricaoAudioService.transcrever(audio);
            return Response.ok(new TranscricaoAudioDto(texto)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"erro\":\"Nao foi possivel transcrever o audio neste momento.\"}")
                    .build();
        }
    }

    @POST
    @Path("/conversa-clinica")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response conversaClinica(@RestForm("audio") FileUpload audio) {
        exigirAcessoClinico();
        try {
            ResultadoConversaClinica resultado = transcricaoAudioService.gerarAnamneseDaConversa(audio);
            return Response.ok(new TranscricaoAudioDto(resultado.anamnese(), resultado.transcricao())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"" + json(e.getMessage()) + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"erro\":\"Nao foi possivel gerar a anamnese da conversa neste momento.\"}")
                    .build();
        }
    }

    private void exigirAcessoClinico() {
        Object usuario = requestContext.getProperty("ghbam.user");
        if (!(usuario instanceof AuthenticatedUser autenticado) || autenticado.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        if (!autenticado.hasAnyPermission(List.of("kmedico", "kenfermagem", "kadm", "kdentista", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia"))) {
            throw new ForbiddenException("Permissao insuficiente para usar ditado clinico.");
        }
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
