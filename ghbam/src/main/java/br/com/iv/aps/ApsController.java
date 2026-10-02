package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;
import static br.com.iv.aps.ApsHttp.ACS;
import static br.com.iv.aps.ApsHttp.APS;
import static br.com.iv.aps.ApsHttp.GESTAO;
import static br.com.iv.aps.ApsHttp.VACINA;
import static br.com.iv.aps.ApsHttp.executar;
import static br.com.iv.aps.ApsHttp.pode;
import static br.com.iv.aps.ApsHttp.proibido;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * GHBAM APS - contexto, referencias LEDI, configuracao, registros LEDI e transmissao.
 */
@Path("api/aps")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ApsController {

    @Inject
    SecurityPermissionService sps;
    @Inject
    ApsContextoService contexto;
    @Inject
    ApsReferenciaService referencias;
    @Inject
    ApsConfiguracaoService configuracao;
    @Inject
    ApsFichaService fichas;
    @Inject
    ApsCdsService cds;
    @Inject
    ApsTransmissaoService transmissao;
    @Inject
    ApsIntegracaoService integracoes;
    @Inject
    ApsDb db;

    @Context
    ContainerRequestContext ctx;

    private AuthenticatedUser u() {
        return ApsHttp.usuario(ctx, sps);
    }

    private String unidade(String informada) {
        return ApsHttp.unidade(ctx, u(), informada);
    }

    // ------------------------------------------------------------------ contexto e referencias

    @GET
    @Path("/contexto")
    public Response contexto(@QueryParam("unidade") String unidade) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> contexto.contextoResumo(user, unidade(unidade), ApsHttp.lotacao(ctx)));
    }

    @GET
    @Path("/referencias")
    public Response referencias() {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> referencias.referencias());
    }

    @GET
    @Path("/referencias/localidades")
    public Response localidades() {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> referencias.localidades());
    }

    @GET
    @Path("/referencias/cbos")
    public Response cbos() {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> referencias.cbos());
    }

    @GET
    @Path("/referencias/catmat")
    public Response catmat(@QueryParam("termo") String termo) {
        if (!pode(u(), APS, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> referencias.buscarCatmat(termo, 40));
    }

    @GET
    @Path("/referencias/sigtap")
    public Response sigtap(@QueryParam("termo") String termo) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> {
            String t = termo == null ? "" : termo.trim();
            if (t.length() < 2) {
                return List.of();
            }
            String dig = t.replaceAll("\\D", "");
            return db.list("""
                    SELECT DISTINCT ON (codigo) codigo, descricao FROM (
                        SELECT CONCAT(sp.pa_id, sp.pa_dv) AS codigo, sp.pa_dc AS descricao, sp.pa_cmp
                        FROM s_proced sp
                        WHERE UPPER(sp.pa_dc) LIKE UPPER(:like)
                           OR (CAST(:dig AS varchar) <> '' AND CONCAT(sp.pa_id, sp.pa_dv) LIKE CAST(:dig AS varchar) || '%')
                    ) x
                    ORDER BY codigo, pa_cmp DESC
                    LIMIT 40
                    """, p("like", "%" + t + "%", "dig", dig.length() >= 4 ? dig : ""));
        });
    }

    @GET
    @Path("/referencias/cid")
    public Response cid(@QueryParam("termo") String termo) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> {
            String t = termo == null ? "" : termo.trim();
            if (t.length() < 2) {
                return List.of();
            }
            return db.list("""
                    SELECT cd_cod AS codigo, cd_descr AS descricao, restrsexo
                    FROM s_cid
                    WHERE UPPER(cd_cod) LIKE UPPER(:cod) OR UPPER(cd_descr) LIKE UPPER(:like)
                    ORDER BY CASE WHEN UPPER(cd_cod) LIKE UPPER(:cod) THEN 0 ELSE 1 END, cd_cod
                    LIMIT 40
                    """, p("cod", t.replace(".", "") + "%", "like", "%" + t + "%"));
        });
    }

    // ------------------------------------------------------------------ configuracao (gestao)

    @GET
    @Path("/config/equipes")
    public Response equipes(@QueryParam("unidade") String unidade, @QueryParam("todas") boolean todas) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> configuracao.equipes(unidade(unidade), false));
    }

    @POST
    @Path("/config/equipes")
    public Response salvarEquipe(Map<String, Object> body) {
        if (!pode(u(), GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> configuracao.salvarEquipe(body, unidade(null)));
    }

    @GET
    @Path("/config/microareas")
    public Response microareas(@QueryParam("idEquipe") Long idEquipe, @QueryParam("unidade") String unidade) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> configuracao.microareas(idEquipe, unidade(unidade)));
    }

    @POST
    @Path("/config/microareas")
    public Response salvarMicroarea(Map<String, Object> body) {
        if (!pode(u(), GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> configuracao.salvarMicroarea(body, unidade(null)));
    }

    @GET
    @Path("/config/lotacoes")
    public Response lotacoes(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> configuracao.lotacoes(unidade(unidade), idEquipe));
    }

    @POST
    @Path("/config/lotacoes")
    public Response salvarLotacao(Map<String, Object> body) {
        if (!pode(u(), GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> configuracao.salvarLotacao(body, unidade(null)));
    }

    // ------------------------------------------------------------------ integracoes (Centralizador e migracao)

    @GET
    @Path("/config/integracoes")
    public Response integracoes() {
        if (!pode(u(), GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> integracoes.listar());
    }

    @PUT
    @Path("/config/integracoes/{chave}")
    public Response salvarIntegracao(@PathParam("chave") String chave, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> integracoes.salvar(chave, body, user.matricula()));
    }

    @POST
    @Path("/config/integracoes/migracao/testar")
    public Response testarMigracao() {
        if (!pode(u(), GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> integracoes.testarMigracao());
    }

    @GET
    @Path("/config/parametros")
    public Response parametros() {
        if (!pode(u(), GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> configuracao.parametros());
    }

    @PUT
    @Path("/config/parametros")
    public Response salvarParametros(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> configuracao.salvarParametros(body, user.matricula()));
    }

    @GET
    @Path("/config/servidores")
    public Response servidores(@QueryParam("termo") String termo) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Gestao da APS");
        }
        return executar(() -> configuracao.buscarServidores(termo, unidade(null)));
    }

    // ------------------------------------------------------------------ registros LEDI

    @GET
    @Path("/fichas")
    public Response listarFichas(@QueryParam("unidade") String unidade, @QueryParam("tipo") Integer tipo,
            @QueryParam("status") String status, @QueryParam("de") String de, @QueryParam("ate") String ate,
            @QueryParam("matricula") String matricula, @QueryParam("idCidadao") Long idCidadao,
            @QueryParam("limite") Integer limite, @QueryParam("offset") Integer offset) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        String mat = pode(user, GESTAO) ? matricula : user.matricula();
        return executar(() -> fichas.listar(unidade(unidade), tipo, status, ApsHttp.data(de, null), ApsHttp.data(ate, null),
                mat, idCidadao, limite == null ? 100 : limite, offset == null ? 0 : offset));
    }

    @GET
    @Path("/fichas/resumo")
    public Response resumoFichas(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> fichas.resumo(unidade(unidade), ApsHttp.data(de, null), ApsHttp.data(ate, null)));
    }

    @GET
    @Path("/fichas/{id}")
    public Response ficha(@PathParam("id") Long id) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> fichas.detalhe(id));
    }

    @GET
    @Path("/fichas/{id}/historico")
    public Response historicoFicha(@PathParam("id") Long id) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> fichas.historico(id));
    }

    /** Registro CDS avulso (visita, atividade coletiva, AD, elegibilidade, consumo alimentar, Zika, procedimentos). */
    @POST
    @Path("/fichas/tipo/{tipo}")
    public Response registrarFicha(@PathParam("tipo") int tipo, Map<String, Object> body) {
        AuthenticatedUser user = u();
        boolean permitido = tipo == 8 ? pode(user, ACS, APS) : pode(user, APS);
        if (!permitido) {
            return proibido("Registros CDS");
        }
        return executar(() -> cds.registrar(tipo, body,
                contexto.lotacao(user, unidade(null), ApsHttp.lotacao(ctx))));
    }

    @PUT
    @Path("/fichas/{id}")
    public Response corrigirFicha(@PathParam("id") Long id, Map<String, Object> dados) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, GESTAO)) {
            return proibido("Registros LEDI");
        }
        return executar(() -> fichas.corrigir(id, dados, user.matricula()));
    }

    @POST
    @Path("/fichas/{id}/cancelar")
    public Response cancelarFicha(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, GESTAO, ACS)) {
            return proibido("Registros LEDI");
        }
        return executar(() -> fichas.cancelar(id, body == null ? null : String.valueOf(body.getOrDefault("motivo", "")), user.matricula()));
    }

    // ------------------------------------------------------------------ transmissao

    @GET
    @Path("/transmissao/status")
    public Response statusTransmissao() {
        if (!pode(u(), GESTAO)) {
            return proibido("Transmissao LEDI");
        }
        return executar(() -> transmissao.status());
    }

    @POST
    @Path("/transmissao/testar")
    public Response testarTransmissao() {
        if (!pode(u(), GESTAO)) {
            return proibido("Transmissao LEDI");
        }
        return executar(() -> transmissao.testarConexao());
    }

    @POST
    @Path("/transmissao/enviar")
    public Response enviar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO)) {
            return proibido("Transmissao LEDI");
        }
        // Envio manual e da unidade logada (o envio automatico do municipio nao passa por aqui).
        String unidade = unidade(body == null ? null : (String) body.get("unidade"));
        int limite = body != null && body.get("limite") instanceof Number n ? n.intValue() : 500;
        return executar(() -> transmissao.enviarPendentes(unidade, limite, user.matricula()));
    }

    @GET
    @Path("/transmissao/zip")
    @Produces("application/zip")
    public Response zip(@QueryParam("unidade") String unidade, @QueryParam("de") String de, @QueryParam("ate") String ate,
            @QueryParam("incluirEnviadas") boolean incluirEnviadas) {
        if (!pode(u(), GESTAO)) {
            return proibido("Transmissao LEDI");
        }
        try {
            // So as fichas da unidade logada: sem isso, ?unidade= vazio exportava todas as unidades.
            byte[] zip = transmissao.exportarZip(unidade(unidade), ApsHttp.data(de, null), ApsHttp.data(ate, null), incluirEnviadas);
            return Response.ok(zip, "application/zip")
                    .header("Content-Disposition", "attachment; filename=\"ledi-aps-" + LocalDate.now() + ".zip\"")
                    .build();
        } catch (RuntimeException e) {
            return ApsHttp.json(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @POST
    @Path("/transmissao/marcar-enviadas")
    public Response marcarEnviadas(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO)) {
            return proibido("Transmissao LEDI");
        }
        return executar(() -> {
            @SuppressWarnings("unchecked")
            List<Object> ids = (List<Object>) body.getOrDefault("ids", List.of());
            transmissao.marcarComoEnviadas(ids.stream().map(o -> ((Number) o).longValue()).toList(), user.matricula());
            return Map.of("ok", true, "total", ids.size());
        });
    }
}
