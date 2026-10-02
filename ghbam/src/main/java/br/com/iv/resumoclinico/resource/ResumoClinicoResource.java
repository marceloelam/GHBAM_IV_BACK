package br.com.iv.resumoclinico.resource;

import java.util.Map;
import br.com.iv.resumoclinico.dto.GerarResumoClinicoRequest;
import br.com.iv.resumoclinico.service.ResumoClinicoProcessoService;
import br.com.iv.resumoclinico.service.ResumoClinicoService;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;

@Path("/resumo-clinico")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResumoClinicoResource {
    private static final Logger LOG = Logger.getLogger(ResumoClinicoResource.class);
    @Inject ResumoClinicoService service;
    @Inject ResumoClinicoProcessoService processos;
    @Inject SecurityPermissionService permissions;
    @Context ContainerRequestContext request;
    @Context HttpHeaders headers;

    @GET @Path("/pacientes")
    public Response pacientes(@QueryParam("termo") String termo) {
        try { return Response.ok(service.buscar(termo, usuario())).build(); }
        catch (WebApplicationException e) { return erro(e.getResponse().getStatus(), e.getMessage()); }
        catch (Exception e) { LOG.error("Falha na pesquisa de pacientes do resumo clínico", e); return erro(500, "Não foi possível pesquisar pacientes."); }
    }

    @GET @Path("/pacientes/{pacienteId}/atendimentos")
    public Response atendimentos(@PathParam("pacienteId") int pacienteId) {
        try { return Response.ok(service.atendimentos(pacienteId, usuario())).build(); }
        catch (WebApplicationException e) { return erro(e.getResponse().getStatus(), e.getMessage()); }
        catch (Exception e) { LOG.error("Falha na pesquisa de atendimentos do resumo clínico", e); return erro(500, "Não foi possível pesquisar atendimentos."); }
    }

    @POST @Path("/gerar")
    public Response gerar(@Valid GerarResumoClinicoRequest dto) {
        try { return Response.ok(service.gerar(dto, usuario(), ip())).build(); }
        catch (IllegalArgumentException e) { return erro(400, e.getMessage()); }
        catch (WebApplicationException e) { return erro(e.getResponse().getStatus(), e.getMessage()); }
        catch (Exception e) { LOG.error("Falha ao gerar resumo clínico", e); return erro(500, "Não foi possível gerar o resumo clínico."); }
    }

    @POST @Path("/gerar/iniciar")
    public Response iniciarGeracao(@Valid GerarResumoClinicoRequest dto) {
        try {
            return Response.accepted(processos.iniciar(dto, usuario(), ip())).build();
        } catch (IllegalArgumentException e) { return erro(400, e.getMessage()); }
        catch (WebApplicationException e) { return erro(e.getResponse().getStatus(), e.getMessage()); }
        catch (Exception e) { LOG.error("Falha ao iniciar resumo clínico", e); return erro(500, "Não foi possível iniciar o resumo clínico."); }
    }

    @GET @Path("/gerar/status/{processoId}")
    public Response progresso(@PathParam("processoId") String processoId) {
        try { return Response.ok(processos.consultar(processoId, usuario())).build(); }
        catch (WebApplicationException e) { return erro(e.getResponse().getStatus(), e.getMessage()); }
        catch (Exception e) { LOG.error("Falha ao consultar progresso do resumo clínico", e); return erro(500, "Não foi possível consultar o andamento."); }
    }

    @GET @Path("/historico")
    public Response historico(@QueryParam("bam") int bam) {
        try { return Response.ok(service.historico(bam, usuario())).build(); }
        catch (WebApplicationException e) { return erro(e.getResponse().getStatus(), e.getMessage()); }
        catch (Exception e) { LOG.error("Falha no histórico de resumos", e); return erro(500, "Não foi possível consultar o histórico."); }
    }

    private AuthenticatedUser usuario() {
        Object atual = request.getProperty("ghbam.user");
        return atual instanceof AuthenticatedUser u ? u : permissions.resolve(request);
    }
    private String ip() {
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        return forwarded == null ? "" : forwarded.split(",")[0].trim();
    }
    private Response erro(int status, String mensagem) {
        return Response.status(status).entity(Map.of("erro", mensagem == null ? "Erro inesperado." : mensagem)).build();
    }
}
