package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.dto.AssinaturaClinicaDto;
import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.AssinaturaConformidadeService;
import br.com.iv.service.AssinaturaClinicaService;
import br.com.iv.service.AssinaturaPendenciaService;
import br.com.iv.service.FhirPreparatoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("assinaturas-clinicas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AssinaturaClinicaController {

    @Inject
    AssinaturaClinicaService service;

    @Inject
    AssinaturaConformidadeService conformidadeService;

    @Inject
    AssinaturaPendenciaService pendenciaService;

    @Inject
    FhirPreparatoryService fhirService;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/bam/{idbam}")
    public Response listarTodasPorBam(@PathParam("idbam") Integer idbam) {
        List<AssinaturaClinica> assinaturas = service.listarPorBam(idbam);
        return Response.ok(assinaturas).build();
    }

    @GET
    @Path("/bam/{idbam}/conformidade")
    public Response conformidadePorBam(@PathParam("idbam") Integer idbam) {
        Map<String, Object> conformidade = conformidadeService.conformidadePorBam(idbam);
        return Response.ok(conformidade).build();
    }

    @GET
    @Path("/pendencias/minhas")
    public Response minhasPendencias(@QueryParam("dias") @DefaultValue("30") int dias) {
        AuthenticatedUser user = currentUser();
        if (user == null || user.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return Response.ok(pendenciaService.minhasPendencias(user, dias)).build();
    }

    @GET
    @Path("/pendencias/relatorio")
    public Response relatorioPendencias(
            @QueryParam("dias") @DefaultValue("30") int dias) {
        AuthenticatedUser user = currentUser();
        if (!podeVerRelatorioAdministrativo(user)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"erro\":\"Acesso restrito a administracao ou chefia.\"}")
                    .build();
        }
        return Response.ok(pendenciaService.relatorioAdministrativo(user, dias)).build();
    }

    @GET
    @Path("/fhir/metadata")
    public Response fhirMetadata() {
        return Response.ok(fhirService.capabilityStatement()).build();
    }

    @GET
    @Path("/fhir/tipo/{tipoDocumento}/bam/{idbam}")
    public Response fhirAssinaturasPorBam(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("idbam") Integer idbam) {
        return Response.ok(fhirService.assinaturasPorBam(tipoDocumento, idbam)).build();
    }

    @GET
    @Path("/fhir/tipo/{tipoDocumento}/documento/{iddocumento}")
    public Response fhirAssinaturasPorDocumento(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("iddocumento") Integer iddocumento) {
        return Response.ok(fhirService.assinaturasPorDocumento(tipoDocumento, iddocumento)).build();
    }

    @GET
    @Path("/tipo/{tipoDocumento}/bam/{idbam}")
    public Response listarPorBam(@PathParam("tipoDocumento") String tipoDocumento, @PathParam("idbam") Integer idbam) {
        List<AssinaturaClinica> assinaturas = service.listarPorBam(tipoDocumento.toUpperCase(), idbam);
        return Response.ok(assinaturas).build();
    }

    @GET
    @Path("/tipo/{tipoDocumento}/documento/{iddocumento}")
    public Response listarPorDocumento(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("iddocumento") Integer iddocumento) {
        List<AssinaturaClinica> assinaturas = service.listarPorDocumento(tipoDocumento.toUpperCase(), iddocumento);
        return Response.ok(assinaturas).build();
    }

    @POST
    @Path("/assinar")
    public Response assinar(AssinaturaClinicaDto dto) {
        try {
            AssinaturaClinica assinatura = service.assinar(dto, currentUser());
            return Response.status(Response.Status.CREATED).entity(assinatura).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("/{idassinatura}/adendos")
    public Response adendar(@PathParam("idassinatura") Integer idassinatura, AssinaturaClinicaDto dto) {
        try {
            AssinaturaClinica adendo = service.adendar(idassinatura, dto, currentUser());
            if (adendo == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.status(Response.Status.CREATED).entity(adendo).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/{idassinatura}/verificacao")
    public Response verificar(@PathParam("idassinatura") Integer idassinatura) {
        return Response.ok(service.verificar(idassinatura)).build();
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : securityPermissionService.resolve(requestContext);
    }

    private boolean podeVerRelatorioAdministrativo(AuthenticatedUser user) {
        return user != null
                && (user.hasPermission("kadm")
                        || user.hasPermission("ksubadm")
                        || user.hasPermission("pcadusuarios")
                        || user.hasPermission("pcadunidades")
                        || user.hasPermission("pescalaenfermagem"));
    }
}
