package br.com.iv.odontologia;

import java.util.List;

import br.com.iv.dto.OdontologiaFilaDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("api/odontologia")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OdontologiaController {
    @Inject
    OdontologiaService service;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/fila/{unidade}")
    public Response fila(@PathParam("unidade") String unidade) {
        if (!podeAcessar()) return proibido();
        List<OdontologiaFilaDto> fila = service.filaOdontologica(unidade, currentUser().idServidor());
        return Response.ok(fila).build();
    }

    @GET
    @Path("/paciente/{idpaciente}")
    public Response listar(@PathParam("idpaciente") Integer idpaciente) {
        if (!podeAcessar()) return proibido();
        List<OdontologiaAtendimento> atendimentos = service.listarPorPaciente(idpaciente);
        return Response.ok(atendimentos).build();
    }

    @GET
    @Path("/{id}")
    public Response localizar(@PathParam("id") Integer id) {
        if (!podeAcessar()) return proibido();
        OdontologiaAtendimentoView view = service.localizar(id);
        return view == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(view).build();
    }

    @POST
    public Response criar(OdontologiaAtendimentoDto dto) {
        if (!podeAcessar()) return proibido();
        try {
            return Response.status(Response.Status.CREATED).entity(service.criar(dto, currentUser())).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e);
        } catch (IllegalStateException e) {
            return erro(Response.Status.UNAUTHORIZED, e);
        }
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Integer id, OdontologiaAtendimentoDto dto) {
        if (!podeAcessar()) return proibido();
        try {
            OdontologiaAtendimentoView view = service.atualizar(id, dto, currentUser());
            return view == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(view).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e);
        } catch (IllegalStateException e) {
            return erro(Response.Status.CONFLICT, e);
        }
    }

    @POST
    @Path("/{id}/assinar")
    public Response assinar(@PathParam("id") Integer id, OdontologiaAtendimentoDto dto) {
        if (!podeAcessar()) return proibido();
        try {
            OdontologiaAtendimentoView view = service.assinar(id, dto, currentUser());
            return view == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(view).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e);
        } catch (IllegalStateException e) {
            return erro(Response.Status.CONFLICT, e);
        }
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : securityPermissionService.resolve(requestContext);
    }

    private boolean podeAcessar() {
        AuthenticatedUser user = currentUser();
        return user != null && !user.matricula().isBlank()
                && user.hasPermission("kdentista");
    }

    private Response proibido() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity("{\"erro\":\"Acesso restrito ao modulo de Odontologia.\"}")
                .build();
    }

    private Response erro(Response.Status status, RuntimeException e) {
        String mensagem = e.getMessage() == null ? "Falha no atendimento odontologico." : e.getMessage();
        return Response.status(status).entity("{\"erro\":\"" + mensagem.replace("\"", "'") + "\"}").build();
    }
}
