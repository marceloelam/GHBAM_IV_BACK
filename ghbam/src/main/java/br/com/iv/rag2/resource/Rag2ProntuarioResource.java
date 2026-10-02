package br.com.iv.rag2.resource;

import java.util.Map;

import br.com.iv.rag2.Rag2IaException;
import br.com.iv.rag2.Rag2NaoEncontradoException;
import br.com.iv.rag2.Rag2PermissaoException;
import br.com.iv.rag2.Rag2ValidacaoException;
import br.com.iv.rag2.dto.Rag2PerguntaDto;
import br.com.iv.rag2.dto.Rag2RespostaDto;
import br.com.iv.rag2.service.Rag2ProntuarioService;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Endpoints do RAG2 PRONTUARIO. Namespace "/rag2/prontuario" para não colidir
 * com o RAG genérico já existente em "/api/rag" (br.com.iv.util.RagResource).
 */
@Path("/rag2/prontuario")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class Rag2ProntuarioResource {

    private static final Logger LOG = Logger.getLogger(Rag2ProntuarioResource.class);

    @Inject
    Rag2ProntuarioService service;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/paciente/buscar")
    public Response buscar(@QueryParam("termo") String termo,
                            @QueryParam("tipoBusca") String tipoBusca,
                            @QueryParam("pagina") @DefaultValue("0") int pagina,
                            @QueryParam("limite") @DefaultValue("20") int limite) {
        try {
            return Response.ok(service.buscarPacientes(termo, tipoBusca, pagina, limite)).build();
        } catch (Rag2ValidacaoException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOG.error("Erro em /rag2/prontuario/paciente/buscar", e);
            return erro(Response.Status.INTERNAL_SERVER_ERROR, "Erro ao buscar pacientes. Tente novamente ou acione a TI se persistir.");
        }
    }

    @POST
    @Path("/perguntar")
    public Response perguntar(Rag2PerguntaDto dto) {
        try {
            if (dto == null) {
                return erro(Response.Status.BAD_REQUEST, "Corpo da requisição ausente.");
            }
            AuthenticatedUser usuario = currentUser();
            Rag2RespostaDto resposta = service.perguntar(dto, usuario);
            return Response.ok(resposta).build();
        } catch (Rag2ValidacaoException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (Rag2NaoEncontradoException e) {
            return erro(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Rag2PermissaoException e) {
            return erro(Response.Status.FORBIDDEN, e.getMessage());
        } catch (Rag2IaException e) {
            LOG.error("Erro na IA em /rag2/prontuario/perguntar", e);
            return erro(Response.Status.BAD_GATEWAY, e.getMessage());
        } catch (Exception e) {
            LOG.error("Erro em /rag2/prontuario/perguntar", e);
            return erro(Response.Status.INTERNAL_SERVER_ERROR, "Erro ao consultar o prontuario. Tente novamente ou acione a TI se persistir.");
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
