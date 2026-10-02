package br.com.iv.analiserisco.resource;

import java.util.Map;

import br.com.iv.analiserisco.dto.AnaliseRiscoRespostaDto;
import br.com.iv.analiserisco.service.AnaliseRiscoService;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/analise-risco")
@Produces(MediaType.APPLICATION_JSON)
public class AnaliseRiscoResource {

    private static final Logger LOG = Logger.getLogger(AnaliseRiscoResource.class);

    @Inject
    AnaliseRiscoService service;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/paciente/{idpaciente}")
    public Response analisar(@PathParam("idpaciente") int idpaciente) {
        try {
            AuthenticatedUser usuario = currentUser();
            AnaliseRiscoRespostaDto resposta = service.analisar(idpaciente, usuario);
            return Response.ok(resposta).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            LOG.error("Erro em /analise-risco/paciente/" + idpaciente, e);
            return erro(Response.Status.BAD_GATEWAY, "Falha ao consultar a IA. Tente novamente em instantes.");
        }
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : securityPermissionService.resolve(requestContext);
    }

    private Response erro(Response.Status status, String mensagem) {
        return Response.status(status)
            .entity(Map.of("erro", mensagem != null ? mensagem : "Erro desconhecido"))
            .build();
    }
}
