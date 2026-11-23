package br.com.iv.aps;

import static br.com.iv.aps.ApsConfiguracaoService.str;
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
import jakarta.ws.rs.DELETE;
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
 * GHBAM APS - fluxo clinico: lista de atendimentos, escuta inicial, atendimento
 * SOAP, problemas/alergias, medicoes, pre-natal, vacinacao, agenda, garantia do
 * acesso e cuidado compartilhado.
 */
@Path("api/aps/clinico")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// Sem unidade usa a da sessao (ApsHttp.unidade): unidade vazia nao e' "todas".
@br.com.iv.security.UnidadePadraoSessao
public class ApsClinicoController {

    @Inject
    SecurityPermissionService sps;
    @Inject
    ApsContextoService contexto;
    @Inject
    ApsAtendimentoService atendimentos;
    @Inject
    ApsVacinacaoService vacinacao;
    @Inject
    ApsAgendaService agenda;
    @Inject
    ApsCuidadoService cuidado;

    @Inject
    ApsRelatorioService relatorios;

    @Context
    ContainerRequestContext ctx;

    private AuthenticatedUser u() {
        return ApsHttp.usuario(ctx, sps);
    }

    private String unidade(String informada) {
        return ApsHttp.unidade(ctx, u(), informada);
    }

    private ApsContextoService.Lotacao lot(AuthenticatedUser user) {
        return contexto.lotacao(user, unidade(null), ApsHttp.lotacao(ctx));
    }

    // ------------------------------------------------------------------ lista de atendimentos

    @GET
    @Path("/lista")
    public Response lista(@QueryParam("data") String data, @QueryParam("status") String status,
            @QueryParam("somenteMeus") boolean somenteMeus) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, VACINA, GESTAO)) {
            return proibido("Lista de atendimentos");
        }
        return executar(() -> atendimentos.lista(unidade(null), ApsHttp.data(data, LocalDate.now()), status,
                somenteMeus ? user.matricula() : null));
    }

    @POST
    @Path("/lista")
    public Response adicionar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, VACINA)) {
            return proibido("Lista de atendimentos");
        }
        return executar(() -> atendimentos.adicionarNaLista(body, unidade(null), user.matricula()));
    }

    @POST
    @Path("/lista/{id}/status")
    public Response statusLista(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, VACINA)) {
            return proibido("Lista de atendimentos");
        }
        return executar(() -> atendimentos.alterarStatusLista(id, str(body.get("status")), user.matricula()));
    }

    @POST
    @Path("/lista/{id}/escuta")
    public Response escuta(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Escuta inicial");
        }
        return executar(() -> atendimentos.registrarEscuta(id, body, lot(user)));
    }

    // ------------------------------------------------------------------ atendimento SOAP

    @POST
    @Path("/atendimentos")
    public Response iniciar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Atendimento");
        }
        return executar(() -> atendimentos.iniciar(body, lot(user)));
    }

    @GET
    @Path("/atendimentos/{id}")
    public Response atendimento(@PathParam("id") Long id) {
        if (!pode(u(), APS, GESTAO)) {
            return proibido("Atendimento");
        }
        return executar(() -> atendimentos.obter(id));
    }

    @PUT
    @Path("/atendimentos/{id}")
    public Response rascunho(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Atendimento");
        }
        return executar(() -> atendimentos.salvarRascunho(id, body, user.matricula()));
    }

    @POST
    @Path("/atendimentos/{id}/finalizar")
    public Response finalizar(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Atendimento");
        }
        return executar(() -> atendimentos.finalizar(id, body, lot(user)));
    }

    @POST
    @Path("/atendimentos/{id}/cancelar")
    public Response cancelarAtendimento(@PathParam("id") Long id) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Atendimento");
        }
        return executar(() -> atendimentos.cancelar(id, user.matricula()));
    }

    // ------------------------------------------------------------------ problemas, medicoes, gestacao

    @GET
    @Path("/cidadaos/{id}/problemas")
    public Response problemas(@PathParam("id") Long id) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Prontuario");
        }
        return executar(() -> atendimentos.problemas(id));
    }

    @POST
    @Path("/cidadaos/{id}/problemas")
    public Response salvarProblema(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Prontuario");
        }
        return executar(() -> atendimentos.salvarProblema(id, body, user.matricula()));
    }

    @GET
    @Path("/cidadaos/{id}/medicoes")
    public Response medicoes(@PathParam("id") Long id) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Prontuario");
        }
        return executar(() -> atendimentos.medicoes(id));
    }

    /** Boas praticas C2-C7 ainda nao cumpridas por este cidadao (lembrete no atendimento). */
    @GET
    @Path("/cidadaos/{id}/pendencias")
    public Response pendencias(@PathParam("id") Long id) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Prontuario");
        }
        return executar(() -> relatorios.pendencias(id, unidade(null)));
    }

    @GET
    @Path("/cidadaos/{id}/gestacao")
    public Response gestacao(@PathParam("id") Long id) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Prontuario");
        }
        return executar(() -> {
            Map<String, Object> g = atendimentos.gestacaoAtiva(id);
            return g == null ? Map.of() : g;
        });
    }

    @POST
    @Path("/cidadaos/{id}/gestacao")
    public Response salvarGestacao(@PathParam("id") Long id, Map<String, Object> body) {
        if (!pode(u(), APS)) {
            return proibido("Pre-natal");
        }
        return executar(() -> atendimentos.salvarGestacao(id, body));
    }

    @POST
    @Path("/gestacoes/{id}/encerrar")
    public Response encerrarGestacao(@PathParam("id") Long id, Map<String, Object> body) {
        if (!pode(u(), APS)) {
            return proibido("Pre-natal");
        }
        return executar(() -> atendimentos.encerrarGestacao(id, body));
    }

    @POST
    @Path("/gestacoes/{id}/encerrar-puerperio")
    public Response encerrarPuerperio(@PathParam("id") Long id) {
        if (!pode(u(), APS)) {
            return proibido("Pre-natal");
        }
        return executar(() -> atendimentos.encerrarPuerperio(id));
    }

    // ------------------------------------------------------------------ vacinacao

    @GET
    @Path("/vacinacao/{idCidadao}")
    public Response caderneta(@PathParam("idCidadao") Long idCidadao) {
        if (!pode(u(), VACINA, APS, GESTAO)) {
            return proibido("Vacinacao");
        }
        return executar(() -> vacinacao.caderneta(idCidadao));
    }

    @POST
    @Path("/vacinacao/{idCidadao}")
    public Response vacinar(@PathParam("idCidadao") Long idCidadao, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, VACINA)) {
            return proibido("Sala de vacina");
        }
        return executar(() -> vacinacao.registrar(idCidadao, body, lot(user)));
    }

    @POST
    @Path("/vacinacao/{idCidadao}/aprazar")
    public Response aprazar(@PathParam("idCidadao") Long idCidadao, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, VACINA, APS)) {
            return proibido("Vacinacao");
        }
        return executar(() -> vacinacao.aprazar(idCidadao, body, lot(user)));
    }

    @POST
    @Path("/vacinacao/registro/{id}/excluir")
    public Response excluirDose(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, VACINA)) {
            return proibido("Sala de vacina");
        }
        return executar(() -> vacinacao.excluir(id, str(body.get("motivo")), lot(user)));
    }

    @GET
    @Path("/vacinacao-lotes")
    public Response lotes(@QueryParam("validos") boolean validos) {
        if (!pode(u(), VACINA, GESTAO)) {
            return proibido("Lotes de imunobiologicos");
        }
        return executar(() -> vacinacao.lotes(unidade(null), validos));
    }

    @POST
    @Path("/vacinacao-lotes")
    public Response salvarLote(Map<String, Object> body) {
        if (!pode(u(), VACINA, GESTAO)) {
            return proibido("Lotes de imunobiologicos");
        }
        return executar(() -> vacinacao.salvarLote(body, unidade(null)));
    }

    @GET
    @Path("/vacinacao-busca-ativa")
    public Response buscaAtiva(@QueryParam("idEquipe") Long idEquipe, @QueryParam("microarea") String microarea) {
        if (!pode(u(), VACINA, APS, GESTAO, ApsHttp.ACS)) {
            return proibido("Busca ativa de vacinacao");
        }
        return executar(() -> vacinacao.buscaAtiva(unidade(null), idEquipe, microarea));
    }

    @GET
    @Path("/vacinacao-calendario")
    public Response calendario() {
        if (!pode(u(), VACINA, APS, GESTAO)) {
            return proibido("Vacinacao");
        }
        return executar(() -> ApsVacinacaoService.CALENDARIO);
    }

    // ------------------------------------------------------------------ agenda

    @GET
    @Path("/agenda")
    public Response agendamentos(@QueryParam("matricula") String matricula, @QueryParam("de") String de,
            @QueryParam("ate") String ate, @QueryParam("idCidadao") Long idCidadao) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Agenda");
        }
        LocalDate hoje = LocalDate.now();
        return executar(() -> agenda.agendamentos(unidade(null), matricula, ApsHttp.data(de, idCidadao == null ? hoje : null),
                ApsHttp.data(ate, idCidadao == null ? hoje.plusDays(30) : null), idCidadao));
    }

    @GET
    @Path("/agenda/horarios")
    public Response horarios(@QueryParam("matricula") String matricula, @QueryParam("data") String data) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Agenda");
        }
        return executar(() -> agenda.horarios(matricula, unidade(null), ApsHttp.data(data, LocalDate.now())));
    }

    @POST
    @Path("/agenda")
    public Response agendar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, VACINA)) {
            return proibido("Agenda");
        }
        return executar(() -> agenda.agendar(body, unidade(null), user.matricula()));
    }

    @POST
    @Path("/agenda/{id}/status")
    public Response statusAgenda(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, VACINA)) {
            return proibido("Agenda");
        }
        return executar(() -> agenda.alterarStatus(id, str(body.get("status")), str(body.get("motivo")), user.matricula()));
    }

    @GET
    @Path("/agenda/configuracao")
    public Response configAgenda(@QueryParam("matricula") String matricula) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, GESTAO)) {
            return proibido("Agenda");
        }
        String m = matricula == null || matricula.isBlank() ? user.matricula() : matricula;
        return executar(() -> Map.of("grade", agenda.configuracao(m, unidade(null)), "fechamentos", agenda.fechamentos(m, unidade(null))));
    }

    @PUT
    @Path("/agenda/configuracao")
    @SuppressWarnings("unchecked")
    public Response salvarConfigAgenda(Map<String, Object> body) {
        AuthenticatedUser user = u();
        String m = str(body.get("matricula")) == null ? user.matricula() : str(body.get("matricula"));
        if (!pode(user, GESTAO) && !(pode(user, APS) && m.equals(user.matricula()))) {
            return proibido("Configuracao de agenda");
        }
        return executar(() -> agenda.salvarConfiguracao(m, unidade(null), (List<Map<String, Object>>) body.getOrDefault("grade", List.of())));
    }

    @POST
    @Path("/agenda/fechamentos")
    public Response fechar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO, APS)) {
            return proibido("Fechamento de agenda");
        }
        return executar(() -> agenda.fecharAgenda(body, unidade(null), user.matricula()));
    }

    @DELETE
    @Path("/agenda/fechamentos/{id}")
    public Response reabrir(@PathParam("id") Long id) {
        if (!pode(u(), GESTAO, APS)) {
            return proibido("Fechamento de agenda");
        }
        return executar(() -> {
            agenda.removerFechamento(id);
            return Map.of("ok", true);
        });
    }

    // ------------------------------------------------------------------ garantia do acesso

    @GET
    @Path("/garantia")
    public Response garantia(@QueryParam("status") String status) {
        if (!pode(u(), APS, GESTAO)) {
            return proibido("Garantia do acesso");
        }
        return executar(() -> agenda.garantia(unidade(null), status == null ? "ABERTO" : status));
    }

    @POST
    @Path("/garantia")
    public Response incluirGarantia(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Garantia do acesso");
        }
        return executar(() -> agenda.incluirGarantia(body, unidade(null), user.matricula()));
    }

    @POST
    @Path("/garantia/{id}/remover")
    public Response removerGarantia(@PathParam("id") Long id, Map<String, Object> body) {
        if (!pode(u(), APS, GESTAO)) {
            return proibido("Garantia do acesso");
        }
        return executar(() -> agenda.removerGarantia(id, str(body.get("motivo"))));
    }

    // ------------------------------------------------------------------ cuidado compartilhado

    @GET
    @Path("/cuidados")
    public Response cuidados(@QueryParam("papel") String papel, @QueryParam("status") String status) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, GESTAO)) {
            return proibido("Cuidado compartilhado");
        }
        return executar(() -> cuidado.listar(unidade(null), user.matricula(), papel, status));
    }

    @GET
    @Path("/cuidados/{id}")
    public Response cuidadoDetalhe(@PathParam("id") Long id) {
        if (!pode(u(), APS, GESTAO)) {
            return proibido("Cuidado compartilhado");
        }
        return executar(() -> cuidado.obter(id));
    }

    @POST
    @Path("/cuidados")
    public Response compartilhar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Cuidado compartilhado");
        }
        return executar(() -> cuidado.compartilhar(body, lot(user)));
    }

    @POST
    @Path("/cuidados/{id}/evoluir")
    public Response evoluir(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS)) {
            return proibido("Cuidado compartilhado");
        }
        return executar(() -> cuidado.evoluir(id, body, lot(user), false));
    }

    @GET
    @Path("/resumo-dia")
    public Response resumoDia() {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>(agenda.resumoDia(unidade(null), LocalDate.now()));
            List<Map<String, Object>> l = atendimentos.lista(unidade(null), LocalDate.now(), null, null);
            m.put("naLista", l.size());
            m.put("aguardando", l.stream().filter(x -> List.of("AGUARDANDO", "AGUARDANDO_ATENDIMENTO").contains(x.get("status"))).count());
            m.put("atendidos", l.stream().filter(x -> "ATENDIDO".equals(x.get("status"))).count());
            return m;
        });
    }
}
