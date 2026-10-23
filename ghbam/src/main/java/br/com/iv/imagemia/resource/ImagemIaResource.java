package br.com.iv.imagemia.resource;

import java.util.Map;

import br.com.iv.imagemia.dto.CienciaImagemIaRequest;
import br.com.iv.imagemia.service.ImagemIaService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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

/**
 * Analise de imagem por IA (MONAI). Desligado por padrao; so responde quando a
 * unidade do profissional esta habilitada. A IA apenas sugere: o achado precisa
 * de ciencia e conduta do profissional (POST ciencia).
 */
@Path("imagem-ia")
@Produces(MediaType.APPLICATION_JSON)
public class ImagemIaResource {

    @Inject
    ImagemIaService service;

    private static AuthenticatedUser usuario(ContainerRequestContext ctx) {
        Object value = ctx == null ? null : ctx.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser u ? u : null;
    }

    @GET
    @Path("habilitado")
    public Response habilitado(@Context ContainerRequestContext ctx) {
        AuthenticatedUser u = usuario(ctx);
        boolean ok = u != null && service.habilitadoPara(u.unidade());
        return Response.ok(Map.of("habilitado", ok)).build();
    }

    @GET
    @Path("bam/{idbam}")
    public Response listar(@PathParam("idbam") int idbam) {
        return Response.ok(service.listarPorBam(idbam)).build();
    }

    @POST
    @Path("analisar")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response analisar(byte[] dicom,
                             @QueryParam("idbam") int idbam,
                             @QueryParam("pacienteId") Integer pacienteId,
                             @QueryParam("idimagens") String idimagens,
                             @QueryParam("descricao") String descricao,
                             @Context ContainerRequestContext ctx) {
        try {
            return Response.ok(service.analisar(idbam, pacienteId, idimagens, descricao, dicom, usuario(ctx))).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("analisar-exame")
    public Response analisarExame(@QueryParam("idbam") int idbam,
                                  @QueryParam("pacienteId") Integer pacienteId,
                                  @QueryParam("idImagem") String idImagem,
                                  @QueryParam("descricao") String descricao,
                                  @Context ContainerRequestContext ctx) {
        try {
            return Response.ok(service.analisarPorExame(idbam, pacienteId, idImagem, descricao, usuario(ctx))).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    /** Mapa de calor do achado principal (regiao que mais pesou; nao delimita lesao). */
    @GET
    @Path("bam/{idbam}/mapa/{id}")
    @Produces("image/jpeg")
    public Response mapaCalor(@PathParam("idbam") int idbam, @PathParam("id") long id) {
        byte[] jpg = service.mapaCalor(idbam, id);
        if (jpg == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(jpg, "image/jpeg").header("Cache-Control", "private, max-age=300").build();
    }

    /** Teste da configuracao salva da unidade (tela /unidades). RBAC: kadm. */
    @GET
    @Path("status")
    public Response status(@QueryParam("sigla") String sigla) {
        try {
            return Response.ok(service.status(sigla)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("ciencia")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ciencia(CienciaImagemIaRequest request, @Context ContainerRequestContext ctx) {
        try {
            return Response.ok(service.registrarCiencia(request, usuario(ctx))).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }
}
