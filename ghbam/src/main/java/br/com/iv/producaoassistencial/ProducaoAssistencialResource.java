package br.com.iv.producaoassistencial;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/producao-assistencial")
@Produces(MediaType.APPLICATION_JSON)
public class ProducaoAssistencialResource {
    @Inject ProducaoAssistencialService service;
    @Inject SecurityPermissionService security;
    @Context ContainerRequestContext request;

    @GET
    public Response gerar(@QueryParam("inicio") String inicio, @QueryParam("fim") String fim,
            @QueryParam("tipos") String tipos) {
        AuthenticatedUser usuario = (AuthenticatedUser) request.getProperty("ghbam.user");
        if (usuario == null) usuario = security.resolve(request);
        if (usuario == null || !usuario.hasPermission("kadm") || usuario.unidade() == null || usuario.unidade().isBlank())
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", "Relatorio restrito ao Administrativo e a unidade da sessao.")).build();
        return Response.ok(service.gerar(data(inicio), data(fim), usuario.unidade().trim(), tipos)).build();
    }

    @GET
    @Path("/detalhes")
    public Response detalhes(@QueryParam("inicio") String inicio, @QueryParam("fim") String fim,
            @QueryParam("tipo") String tipo, @QueryParam("profissional") String profissional,
            @QueryParam("registro") String registro, @QueryParam("matricula") String matricula,
            @QueryParam("categoria") String categoria, @DefaultValue("0") @QueryParam("pagina") int pagina,
            @DefaultValue("50") @QueryParam("tamanho") int tamanho) {
        AuthenticatedUser usuario = (AuthenticatedUser) request.getProperty("ghbam.user");
        if (usuario == null) usuario = security.resolve(request);
        if (usuario == null || !usuario.hasPermission("kadm") || usuario.unidade() == null || usuario.unidade().isBlank())
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Acesso administrativo necessario.")).build();
        return Response.ok(service.detalhes(data(inicio), data(fim), usuario.unidade().trim(), tipo,
                profissional, registro, matricula, categoria, pagina, tamanho)).header("Cache-Control", "no-store").build();
    }

    @GET
    @Path("/conteudo")
    public Response conteudo(@QueryParam("inicio") String inicio, @QueryParam("fim") String fim,
            @QueryParam("tipo") String tipo, @QueryParam("profissional") String profissional,
            @QueryParam("registro") String registro, @QueryParam("matricula") String matricula,
            @QueryParam("categoria") String categoria, @QueryParam("id") String id) {
        AuthenticatedUser usuario = (AuthenticatedUser) request.getProperty("ghbam.user");
        if (usuario == null) usuario = security.resolve(request);
        if (usuario == null || !usuario.hasPermission("kadm") || usuario.unidade() == null || usuario.unidade().isBlank())
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Acesso administrativo necessario.")).build();
        return Response.ok(service.conteudoRegistro(data(inicio), data(fim), usuario.unidade().trim(), tipo,
                profissional, registro, matricula, categoria, id)).header("Cache-Control", "no-store").build();
    }

    private static LocalDate data(String valor) {
        try { return LocalDate.parse(valor == null ? "" : valor); }
        catch (DateTimeParseException e) { throw new BadRequestException("Informe as datas no formato yyyy-MM-dd."); }
    }
}
