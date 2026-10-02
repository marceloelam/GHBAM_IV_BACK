package br.com.iv.cdss.resource;

import java.util.Map;
import java.time.LocalDate;
import java.time.LocalTime;
import br.com.iv.cdss.service.PreventivoService;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.dto.LogDto;
import br.com.iv.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

@Path("cdss/preventivo/paciente/{id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PreventivoResource {
    @Inject PreventivoService service;
    @Inject LogService logService;

    static AuthenticatedUser autorizar(ContainerRequestContext context, boolean escrita) {
        Object value = context.getProperty("ghbam.user");
        if (!(value instanceof AuthenticatedUser user) || user.matricula().isBlank()) throw new NotAuthorizedException("Bearer");
        boolean clinico = user.hasPermission("kmedico") || user.hasPermission("kenfermagem");
        boolean leituraAutorizada = clinico || user.hasPermission("pprontuariocompleto") || user.hasPermission("kadm");
        if ((escrita && !clinico) || (!escrita && !leituraAutorizada)) throw new ForbiddenException();
        if (user.unidade() == null || user.unidade().isBlank() || "TODAS".equalsIgnoreCase(user.unidade())) throw new ForbiddenException();
        return user;
    }

    @GET
    public Map<String, Object> avaliar(@PathParam("id") int id, @Context ContainerRequestContext context) {
        AuthenticatedUser user = autorizar(context, false);
        Map<String, Object> result = service.avaliar(id, user);
        auditar(user, id, "CONSULTA");
        return result;
    }

    @POST
    @Path("alertas")
    public Response registrar(@PathParam("id") int id, PreventivoService.Registro registro, @Context ContainerRequestContext context) {
        AuthenticatedUser user = autorizar(context, true);
        var item = service.registrar(id, registro, user);
        auditar(user, id, "REGISTRO item=" + item.getIditem());
        return Response.status(201).entity(item).build();
    }

    @POST
    @Path("alertas/{item}/encerrar")
    public Response encerrar(@PathParam("id") int id, @PathParam("item") int item, PreventivoService.Registro registro, @Context ContainerRequestContext context) {
        AuthenticatedUser user = autorizar(context, true);
        var resultado = service.encerrar(id, item, registro, user);
        auditar(user, id, "ENCERRAMENTO item=" + item);
        return Response.ok(resultado).build();
    }

    @POST
    @Path("alertas/{item}/status")
    public Response atualizarStatus(@PathParam("id") int id, @PathParam("item") int item, PreventivoService.AtualizacaoStatus atualizacao, @Context ContainerRequestContext context) {
        AuthenticatedUser user = autorizar(context, true);
        var resultado = service.atualizarStatus(id, item, atualizacao, user);
        auditar(user, id, "STATUS item=" + item + " status=" + resultado.getStatus());
        return Response.ok(resultado).build();
    }

    private void auditar(AuthenticatedUser user, int paciente, String acao) {
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().toString().substring(0, 5));
        log.setUnidade(user.unidade());
        log.setEvento("CDSS_PREVENTIVO " + acao + " paciente=" + paciente + " profissional=" + user.matricula());
        logService.salvarLogAsync(log);
    }
}
