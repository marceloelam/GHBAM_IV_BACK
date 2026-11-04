package br.com.iv.pacienteunificacao;

import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
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
import java.util.function.Supplier;

/**
 * Unificar cadastros duplicados do paciente (Correcao de BAM). Exige kadm ou pcorrecaobam,
 * no RoutePermissionCatalog e conferido aqui tambem.
 */
@Path("paciente-unificacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PacienteUnificacaoController {

    private static final List<String> PERMITIDOS = List.of("kadm", "pcorrecaobam");

    @Context
    ContainerRequestContext context;

    @Inject
    PacienteUnificacaoService service;

    private AuthenticatedUser autorizado() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || a.matricula() == null || a.matricula().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        if (!a.hasAnyPermission(PERMITIDOS)) throw new ForbiddenException();
        return a;
    }

    @GET
    @Path("candidatos/{idpaciente}")
    public Response candidatos(@PathParam("idpaciente") int idpaciente) {
        autorizado();
        return executar(() -> service.candidatos(idpaciente));
    }

    @GET
    @Path("previa")
    public Response previa(@QueryParam("origem") int origem, @QueryParam("destino") int destino) {
        autorizado();
        return executar(() -> service.previa(origem, destino));
    }

    @POST
    @Path("unificar")
    public Response unificar(Map<String, Object> corpo) {
        AuthenticatedUser u = autorizado();
        int origem = numero(corpo, "origem");
        int destino = numero(corpo, "destino");
        String motivo = corpo == null || corpo.get("motivo") == null ? null : String.valueOf(corpo.get("motivo"));
        return executar(() -> service.unificar(origem, destino, motivo, u.matricula(), u.unidade()));
    }

    private static int numero(Map<String, Object> corpo, String chave) {
        Object v = corpo == null ? null : corpo.get(chave);
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Response executar(Supplier<Object> acao) {
        try {
            return Response.ok(acao.get()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("mensagem", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("mensagem", e.getMessage())).build();
        }
    }
}
