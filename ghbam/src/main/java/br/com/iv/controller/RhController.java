package br.com.iv.controller;

import java.time.LocalDate;

import br.com.iv.dto.RhDto;
import br.com.iv.service.RhService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("rh")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RhController {

    @Inject
    RhService rhService;

    // ===== Plantões =====

    @GET
    @Path("/plantoes/{unidade}")
    public Response listarPlantoes(
            @PathParam("unidade") String unidade,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("30") int dias) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(dias - 1));
        return Response.ok(rhService.listarPlantoes(unidade, dataInicio, dataFim)).build();
    }

    @POST
    @Path("/plantoes")
    public Response abrirPlantao(RhDto.PlantaoRequest req) {
        try {
            return Response.ok(rhService.abrirPlantao(req)).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/plantoes/lote")
    public Response abrirPlantaoLote(RhDto.PlantaoLoteRequest req) {
        if (req == null || req.plantoes == null || req.plantoes.isEmpty())
            return Response.status(400).entity("Lista vazia").build();
        int[] r = rhService.abrirPlantaoLote(req.plantoes);
        return Response.ok("{\"salvos\":" + r[0] + ",\"ignorados\":" + r[1] + "}").build();
    }

    @PUT
    @Path("/plantoes/{id}")
    public Response atualizarPlantao(@PathParam("id") int id, RhDto.PlantaoRequest req) {
        rhService.atualizarPlantao(id, req);
        return Response.noContent().build();
    }

    @GET
    @Path("/servidores/{unidade}")
    public Response listarServidoresPorTipo(
            @PathParam("unidade") String unidade,
            @QueryParam("tipo") @DefaultValue("") String tipo) {
        return Response.ok(rhService.listarServidoresPorTipo(unidade, tipo)).build();
    }

    @PUT
    @Path("/plantoes/{id}/encerrar")
    public Response encerrarPlantao(@PathParam("id") int id) {
        try {
            rhService.encerrarPlantao(id);
        } catch (IllegalStateException e) {
            return Response.status(409).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }

    @DELETE
    @Path("/plantoes/{id}")
    public Response excluirPlantao(@PathParam("id") int id) {
        rhService.excluirPlantao(id);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/plantoes/{id}/escala")
    public Response excluirEscala(@PathParam("id") int id) {
        return Response.ok("{\"excluidos\":" + rhService.excluirEscala(id) + "}").build();
    }

    // ===== Produtividade =====

    @GET
    @Path("/produtividade/{unidade}")
    public Response produtividade(
            @PathParam("unidade") String unidade,
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("dias") @DefaultValue("30") int dias,
            @QueryParam("servidorId") Integer servidorId) {
        LocalDate dataFim = parseData(fim, LocalDate.now());
        LocalDate dataInicio = parseData(inicio, dataFim.minusDays(dias - 1));
        return Response.ok(rhService.produtividade(unidade, dataInicio, dataFim, servidorId)).build();
    }

    // ===== Treinamentos =====

    @GET
    @Path("/treinamentos/{unidade}")
    public Response listarTreinamentos(@PathParam("unidade") String unidade) {
        return Response.ok(rhService.listarTreinamentos(unidade)).build();
    }

    @POST
    @Path("/treinamentos")
    public Response criarTreinamento(RhDto.TreinamentoRequest req) {
        return Response.ok(rhService.criarTreinamento(req)).build();
    }

    @PUT
    @Path("/treinamentos/{tid}/participante/{sid}/concluir")
    public Response concluirParticipante(@PathParam("tid") int tid, @PathParam("sid") int sid) {
        rhService.concluirParticipante(tid, sid);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/treinamentos/{id}")
    public Response excluirTreinamento(@PathParam("id") int id) {
        rhService.excluirTreinamento(id);
        return Response.noContent().build();
    }

    // ===== Certificações =====

    @GET
    @Path("/certificacoes/{unidade}")
    public Response listarCertificacoes(@PathParam("unidade") String unidade) {
        return Response.ok(rhService.listarCertificacoes(unidade)).build();
    }

    @POST
    @Path("/certificacoes")
    public Response registrarCertificacao(RhDto.CertificacaoRequest req) {
        return Response.ok(rhService.registrarCertificacao(req)).build();
    }

    @DELETE
    @Path("/certificacoes/{id}")
    public Response excluirCertificacao(@PathParam("id") int id) {
        rhService.excluirCertificacao(id);
        return Response.noContent().build();
    }

    private LocalDate parseData(String valor, LocalDate padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        return LocalDate.parse(valor);
    }
}
