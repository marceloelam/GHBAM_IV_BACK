package br.com.iv.cdss.resource;

import java.util.List;

import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.cdss.dto.CdssOverrideRequest;
import br.com.iv.cdss.dto.News2Request;
import br.com.iv.cdss.entity.CdssAlertaOverride;
import br.com.iv.cdss.service.CdssService;
import br.com.iv.security.AuthenticatedUser;
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

@Path("cdss")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CdssResource {

    @Inject
    CdssService service;

    @POST
    @Path("prescricao/avaliar")
    public Response avaliarPrescricao(CdssAvaliacaoPrescricaoRequest request) {
        try {
            return Response.ok(service.avaliarPrescricao(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("scores/news2")
    public Response calcularNews2(News2Request request) {
        try {
            return Response.ok(service.calcularNews2(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("alertas/override")
    public Response registrarOverride(CdssOverrideRequest request, @Context ContainerRequestContext context) {
        try {
            preencherUsuarioAutenticado(request, context);
            return Response.status(Response.Status.CREATED).entity(service.registrarOverride(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("alertas/override/bam/{idbam}")
    public Response listarOverridesPorBam(@PathParam("idbam") int idbam) {
        List<CdssAlertaOverride> overrides = service.listarOverridesPorBam(idbam);
        return Response.ok(overrides).build();
    }

    private void preencherUsuarioAutenticado(CdssOverrideRequest request, ContainerRequestContext context) {
        if (request == null || context == null) return;
        Object value = context.getProperty("ghbam.user");
        if (!(value instanceof AuthenticatedUser user) || user.matricula().isBlank()) return;
        if (request.getUsuarioMatricula() == null || request.getUsuarioMatricula().isBlank()) {
            request.setUsuarioMatricula(user.matricula());
        }
        if (request.getUsuarioNome() == null || request.getUsuarioNome().isBlank()) {
            request.setUsuarioNome(user.nome());
        }
        if (request.getUnidade() == null || request.getUnidade().isBlank()) {
            request.setUnidade(user.unidade());
        }
    }
}
