package br.com.iv.controller;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FhirDispatchService;
import br.com.iv.service.FhirPreparatoryService;
import br.com.iv.service.FhirResultadoLaboratorioExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Path("interoperabilidade")
@Produces(MediaType.APPLICATION_JSON)
public class InteroperabilidadeController {

    @Inject
    FhirPreparatoryService fhirService;

    @Inject
    FhirDispatchService fhirDispatchService;

    @Inject
    FhirResultadoLaboratorioExportService fhirResultadoLaboratorioExportService;

    /** Exportacao FHIR preparatoria; nao transmite automaticamente para a RNDS. */
    @GET
    @Path("/fhir/resultados-laboratorio/bam/{idbam}")
    public Response resultadosLaboratorio(@PathParam("idbam") Integer idbam,
            @Context ContainerRequestContext context) {
        try {
            return Response.ok(fhirResultadoLaboratorioExportService.exportar(idbam, user(context))).build();
        } catch (FhirResultadoLaboratorioExportService.FhirResultadoLaboratorioAccessException e) {
            return Response.status(e.isNotFound() ? Response.Status.NOT_FOUND : Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/fhir/resultados-laboratorio/bam/{idbam}/homologacao")
    public Response avaliarResultadosLaboratorio(@PathParam("idbam") Integer idbam,
            @Context ContainerRequestContext context) {
        try {
            return Response.ok(fhirResultadoLaboratorioExportService
                    .avaliarHomologacao(idbam, user(context))).build();
        } catch (FhirResultadoLaboratorioExportService.FhirResultadoLaboratorioAccessException e) {
            return Response.status(e.isNotFound() ? Response.Status.NOT_FOUND : Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/fhir/envios/tipo/{tipoDocumento}/documento/{iddocumento}")
    public Response statusEnvio(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("iddocumento") Integer iddocumento) {
        return fhirDispatchService.statusPorDocumento(tipoDocumento.toUpperCase(), iddocumento)
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).entity("Nenhum envio FHIR registrado para este documento.").build());
    }

    // Tabela da tela de consulta: lista os envios da unidade, mais recente primeiro,
    // com filtro opcional de periodo. dataInicio/dataFim vem como aaaa-mm-dd (o mesmo
    // formato que um <input type="date"> devolve) e viram o dia inteiro (00:00 a 23:59:59).
    @GET
    @Path("/fhir/envios/tipo/{tipoDocumento}/unidade/{unidade}")
    public Response listarEnvios(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("unidade") String unidade,
            @QueryParam("dataInicio") String dataInicio,
            @QueryParam("dataFim") String dataFim,
            @QueryParam("fila") boolean fila) {
        try {
            var inicio = (dataInicio == null || dataInicio.isBlank()) ? null
                    : LocalDate.parse(dataInicio).atStartOfDay();
            var fim = (dataFim == null || dataFim.isBlank()) ? null
                    : LocalDate.parse(dataFim).atTime(LocalTime.MAX);
            // fila=true: tabela "Fila de Resumos de Alta" (todos os status); sem o
            // parametro, mantem o contrato antigo (so ENVIADO, "Envios concluidos").
            if (fila) {
                return Response.ok(fhirDispatchService.listarFilaPorUnidade(tipoDocumento.toUpperCase(),
                        unidade, inicio, fim)).build();
            }
            return Response.ok(fhirDispatchService.listarPorUnidade(tipoDocumento.toUpperCase(),
                    unidade.toUpperCase(), inicio, fim)).build();
        } catch (java.time.format.DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Data invalida - use aaaa-mm-dd.").build();
        }
    }

    @POST
    @Path("/fhir/envios/{id}/reenviar")
    public Response reenviar(@PathParam("id") Long id, @Context ContainerRequestContext context) {
        try {
            return Response.ok(fhirDispatchService.reenviarManual(id, user(context))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @jakarta.inject.Inject
    br.com.iv.service.FhirTesteRndsService fhirTesteRndsService;

    /** Teste de conexao com a RNDS de homologacao usando paciente ficticio (nao grava, nao usa a fila). */
    @POST
    @Path("/fhir/teste-rnds/unidade/{unidade}")
    public Response testarRnds(@PathParam("unidade") String unidade,
            @jakarta.ws.rs.QueryParam("simular") boolean simular) {
        try {
            return Response.ok(fhirTesteRndsService.testar(unidade, simular)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("erro", e.getMessage())).build();
        }
    }

    private AuthenticatedUser user(ContainerRequestContext context) {
        Object value = context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : AuthenticatedUser.empty();
    }

    @GET
    @Path("/fhir/metadata")
    public Response metadata() {
        return Response.ok(fhirService.capabilityStatement()).build();
    }

    @GET
    @Path("/fhir/assinaturas/tipo/{tipoDocumento}/bam/{idbam}")
    public Response assinaturasPorBam(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("idbam") Integer idbam) {
        return Response.ok(fhirService.assinaturasPorBam(tipoDocumento, idbam)).build();
    }

    @GET
    @Path("/fhir/assinaturas/tipo/{tipoDocumento}/documento/{iddocumento}")
    public Response assinaturasPorDocumento(
            @PathParam("tipoDocumento") String tipoDocumento,
            @PathParam("iddocumento") Integer iddocumento) {
        return Response.ok(fhirService.assinaturasPorDocumento(tipoDocumento, iddocumento)).build();
    }
}
