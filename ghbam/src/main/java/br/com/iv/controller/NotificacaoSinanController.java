package br.com.iv.controller;

import br.com.iv.dto.NotificacaoSinanDto;
import br.com.iv.entity.NotificacaoSinan;
import br.com.iv.service.NotificacaoSinanService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;

@Path("notificacoes-sinan")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NotificacaoSinanController {
    @Inject NotificacaoSinanService service;

    private static final Set<String> TIPOS_VALIDOS = Set.of(
        "NOTIFICACAO_INDIVIDUAL", "ACIDENTE_TRABALHO_GRAVE", "SIFILIS_CONGENITA",
        "SIFILIS_GESTANTE", "INTOXICACAO_EXOGENA", "GESTANTE_HIV",
        "DENGUE_CHIKUNGUNYA", "VIOLENCIA_INTERPESSOAL", "RAIVA_HUMANA",
        "ACIDENTE_MATERIAL_BIOLOGICO", "ACIDENTE_TRANSITO", "AIDS_ADULTO",
        "AIDS_CRIANCA", "ANIMAIS_PECONHENTOS", "BOTULISMO", "CHAGAS_AGUDA",
        "CHAGAS_CRONICA", "COLERA", "COQUELUCHE", "CRIANCA_EXPOSTA_HIV",
        "ESQUISTOSSOMOSE", "EXANTEMATICA", "FEBRE_AMARELA", "FEBRE_MACULOSA",
        "FEBRE_TIFOIDE", "SINDROME_GRIPAL_COVID", "HANSENIASE", "HEPATITES_VIRAIS",
        "LEPTOSPIROSE", "LTA", "LV", "MALARIA", "MENINGITE", "SRC", "SURTO",
        "SURTO_DTA", "PLANILHA_SURTO", "TOXOPLASMOSE_CONGENITA",
        "TOXOPLASMOSE_GESTACIONAL", "TUBERCULOSE"
    );

    @GET
    public Response listar(@QueryParam("unidade") String unidade) {
        if (unidade == null || unidade.isBlank()) return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"Unidade obrigatoria\"}").build();
        return Response.ok(service.listarPorUnidade(unidade)).build();
    }

    @POST
    public Response salvar(NotificacaoSinanDto dto) {
        if (dto == null || dto.getTipo() == null || dto.getTipo().isBlank() || dto.getDados() == null || dto.getDados().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"Tipo e dados da notificacao sao obrigatorios\"}").build();
        }
        if (!TIPOS_VALIDOS.contains(dto.getTipo())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"Tipo de notificacao invalido\"}").build();
        }
        try {
            NotificacaoSinan item = service.salvar(dto);
            return Response.status(Response.Status.CREATED).entity(item).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("erro", e.getMessage())).build();
        }
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") Long id, NotificacaoSinanDto dto) {
        if (dto == null || dto.getDados() == null || dto.getDados().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"Dados da notificacao sao obrigatorios\"}").build();
        }
        try {
            NotificacaoSinan item = service.atualizar(id, dto);
            if (item == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"erro\":\"Notificacao nao encontrada\"}").build();
            }
            return Response.ok(item).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(java.util.Map.of("erro", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("{id}")
    public Response excluir(@PathParam("id") Long id) {
        try {
            boolean removido = service.excluir(id);
            if (!removido) {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"erro\":\"Notificacao nao encontrada\"}").build();
            }
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(java.util.Map.of("erro", e.getMessage())).build();
        }
    }
}
