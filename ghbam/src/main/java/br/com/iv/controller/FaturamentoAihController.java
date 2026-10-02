package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
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

import br.com.iv.dto.CriarPreAihDto;
import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoAihService;

@Path("faturamento/aih")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoAihController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoAihService aihService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    @GET
    public List<FaturamentoAih> listar(@QueryParam("idcompetencia") Integer idcompetencia) {
        AuthenticatedUser u = user();
        return aihService.listar(idcompetencia, u);
    }

    @GET
    @Path("laudos")
    public Response listarLaudos(@QueryParam("idcompetencia") Integer idcompetencia, @QueryParam("q") String busca) {
        AuthenticatedUser u = user();
        return Response.ok(aihService.listarLaudosElegiveis(idcompetencia, busca, u)).build();
    }

    @GET
    @Path("{id}")
    public Map<String, Object> contaHospitalar(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        return aihService.contaHospitalar(id, u);
    }

    @GET
    @Path("arquivo")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response baixarArquivo(@QueryParam("idcompetencia") Integer idcompetencia) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("bpaexportar", "aihauditar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            ConteudoExportado arquivo = aihService.exportarArquivo(idcompetencia, u);
            return Response.ok(arquivo.conteudo())
                    .header("Content-Disposition", "attachment; filename=AIH_SISAIH01_" + idcompetencia + ".txt")
                    .header("X-GHBAM-Layout", arquivo.layout())
                    .build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }
    }

    @POST
    public Response criarPreAih(CriarPreAihDto dto) {
        AuthenticatedUser u = user();
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(aihService.criarPreAih(dto == null || dto.getIdinternacao() == null ? 0 : dto.getIdinternacao(), u))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("laudo/{idlaudo}")
    public Response criarPreAihPorLaudo(@PathParam("idlaudo") int idlaudo) {
        AuthenticatedUser u = user();
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(aihService.criarPreAihPorLaudo(idlaudo, u))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("competencia/{idcompetencia}")
    public Response criarPreAihsDaCompetencia(@PathParam("idcompetencia") int idcompetencia,
            @QueryParam("q") String busca) {
        AuthenticatedUser u = user();
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(aihService.criarPreAihsDaCompetencia(idcompetencia, busca, u))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("fechar/{id}")
    public Response fechar(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("aihauditar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(aihService.fechar(id, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    // Fechamento da competencia inteira. Mesma permissao do fechar individual - e o mesmo
    // ato, so que em lote. Nunca devolve erro por conta recusada: o placar traz o que
    // fechou, o que foi pulado por internacao e o motivo de cada uma que continua aberta.
    @POST
    @Path("fechar-competencia/{idcompetencia}")
    public Response fecharCompetencia(@PathParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("aihauditar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(aihService.fecharCompetencia(idcompetencia, unidade, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("numerar-fechadas/{idcompetencia}")
    public Response numerarFechadas(@PathParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("aihauditar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(aihService.numerarFechadasSemNumero(idcompetencia, unidade, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Reabre a conta para recompo-la depois que a producao que ficou de fora for
    // corrigida em /producao. Mesma permissao do fechar: quem fecha e quem desfaz.
    @POST
    @Path("reabrir/{id}")
    public Response reabrir(@PathParam("id") int id, @QueryParam("motivo") String motivo) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("aihauditar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(aihService.reabrir(id, motivo, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    // Cancelar e da mesma familia de fechar/reabrir: decisao sobre o destino da conta,
    // com motivo obrigatorio no rastro.
    @POST
    @Path("cancelar/{id}")
    public Response cancelar(@PathParam("id") int id, @QueryParam("motivo") String motivo) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("aihauditar", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(aihService.cancelar(id, motivo, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("classificar-pendentes/{id}")
    public Response classificarPendentes(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("aihauditar", "pfaturamentosuscorrigir", "kadm"))) {
            throw new ForbiddenException();
        }
        try {
            return Response.ok(aihService.classificarPendentesDaConta(id, u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }
}
