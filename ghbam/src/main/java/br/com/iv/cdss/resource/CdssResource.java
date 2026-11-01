package br.com.iv.cdss.resource;

import java.util.List;
import java.util.Map;

import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.cdss.dto.CienciaResultadoCriticoRequest;
import br.com.iv.cdss.dto.CdssOverrideRequest;
import br.com.iv.cdss.dto.News2Request;
import br.com.iv.cdss.entity.CdssAlertaOverride;
import br.com.iv.cdss.service.CdssService;
import br.com.iv.cdss.service.ResultadoCriticoService;
import br.com.iv.cdss.service.ConferenciaMedicamentoService;
import br.com.iv.cdss.dto.ConferenciaMedicamentoRequest;
import br.com.iv.cdss.dto.ReconciliacaoDecisaoRequest;
import br.com.iv.cdss.service.ReconciliacaoMedicamentosService;
import br.com.iv.cdss.service.CdssBaseFarmaceuticaRegras;
import br.com.iv.cdss.service.PlantaoEnfermagemService;
import br.com.iv.cdss.dto.PassagemPlantaoRequest;
import br.com.iv.cdss.dto.DispositivoInvasivoRequest;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("cdss")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CdssResource {

    @Inject
    CdssService service;

    @Inject
    ResultadoCriticoService resultadoCriticoService;

    @Inject
    ConferenciaMedicamentoService conferenciaMedicamentoService;

    @Inject
    ReconciliacaoMedicamentosService reconciliacaoService;

    @Inject
    CdssBaseFarmaceuticaRegras regrasBaseFarmaceutica;

    @Inject
    PlantaoEnfermagemService plantaoService;

    @Inject
    br.com.iv.cdss.service.DeterioracaoNews2Service news2Service;

    @Inject
    br.com.iv.cdss.service.CicloMedicacaoFilaService cicloFilaService;

    @Inject
    br.com.iv.cdss.service.DispositivoInvasivoService dispositivoService;

    @Inject
    br.com.iv.cdss.service.IndicadoresSegurancaService indicadoresService;

    @Inject
    br.com.iv.cdss.service.CdssRegraDoseService regraDoseService;

    @POST
    @Path("prescricao/avaliar")
    public Response avaliarPrescricao(CdssAvaliacaoPrescricaoRequest request) {
        try {
            return Response.ok(service.avaliarPrescricao(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("scores/news2")
    public Response calcularNews2(News2Request request) {
        try {
            return Response.ok(service.calcularNews2(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("alertas/override")
    public Response registrarOverride(CdssOverrideRequest request, @Context ContainerRequestContext context) {
        try {
            preencherUsuarioAutenticado(request, context);
            return Response.status(Response.Status.CREATED).entity(service.registrarOverride(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"erro\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("alertas/override/bam/{idbam}")
    public Response listarOverridesPorBam(@PathParam("idbam") int idbam) {
        List<CdssAlertaOverride> overrides = service.listarOverridesPorBam(idbam);
        return Response.ok(overrides).build();
    }

    @GET
    @Path("resultado-critico/bam/{idbam}")
    public Response listarResultadosCriticos(@PathParam("idbam") int idbam) {
        return Response.ok(resultadoCriticoService.listarPorBam(idbam)).build();
    }

    @POST
    @Path("resultado-critico/ciencia")
    public Response registrarCienciaResultadoCritico(CienciaResultadoCriticoRequest request,
            @Context ContainerRequestContext context) {
        Object value = context == null ? null : context.getProperty("ghbam.user");
        AuthenticatedUser usuario = value instanceof AuthenticatedUser user ? user : null;
        try {
            return Response.ok(resultadoCriticoService.registrarCiencia(request, usuario)).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("conferencia-medicamento")
    public Response conferirMedicamento(ConferenciaMedicamentoRequest request, @Context ContainerRequestContext context) {
        Object value = context == null ? null : context.getProperty("ghbam.user");
        AuthenticatedUser usuario = value instanceof AuthenticatedUser user ? user : null;
        try {
            return Response.ok(conferenciaMedicamentoService.conferir(request, usuario)).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @GET
    @Path("plantao")
    public Response plantaoEnfermagem(@Context ContainerRequestContext context) {
        Object value = context == null ? null : context.getProperty("ghbam.user");
        AuthenticatedUser usuario = value instanceof AuthenticatedUser user ? user : null;
        try {
            // Unidade sempre da sessao: nunca aceita unidade vinda do cliente.
            return Response.ok(plantaoService.montar(usuario == null ? null : usuario.unidade())).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    /** Fila do ciclo de medicacao da unidade da sessao: pacientes com etapa pendente para o perfil. */
    @GET
    @Path("ciclo/fila")
    public Response filaCicloMedicacao(@jakarta.ws.rs.QueryParam("perfil") String perfil, @Context ContainerRequestContext context) {
        return executar(() -> {
            AuthenticatedUser u = usuarioDaSessao(context);
            return Response.ok(cicloFilaService.fila(u == null ? null : u.unidade(), perfil)).build();
        });
    }

    @GET
    @Path("ciclo/config")
    public Response configCicloMedicacao(@Context ContainerRequestContext context) {
        return executar(() -> {
            AuthenticatedUser u = usuarioDaSessao(context);
            return Response.ok(cicloFilaService.config(u == null ? null : u.unidade())).build();
        });
    }

    /** Somente administrador (kadm); a unidade e sempre a da sessao. */
    @jakarta.ws.rs.PUT
    @Path("ciclo/config")
    public Response salvarConfigCicloMedicacao(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(cicloFilaService.salvarConfig(
                !Boolean.FALSE.equals(corpo == null ? null : corpo.get("farmaciaDispensa")), usuarioDaSessao(context))).build());
    }

    /** Peso mais recente do BAM (sinais vitais ou triagem) para a calculadora de dose. 204 sem dado. */
    @GET
    @Path("peso/bam/{idbam}")
    public Response pesoDoBam(@PathParam("idbam") int idbam) {
        Map<String, Object> r = news2Service.pesoDoBam(idbam);
        return r == null ? Response.noContent().build() : Response.ok(r).build();
    }

    /** NEWS2/qSOFA de um BAM (sinais vitais da enfermagem ou, sem eles, da triagem). 204 sem dado. */
    @GET
    @Path("news2/bam/{idbam}")
    public Response news2DoBam(@PathParam("idbam") int idbam) {
        Map<String, Object> r = news2Service.doBam(idbam);
        return r == null ? Response.noContent().build() : Response.ok(r).build();
    }

    /** NEWS2 automatico dos internados da unidade da sessao (idbam -> escore), para a visao medica. */
    @GET
    @Path("news2/internados")
    public Response news2Internados(@Context ContainerRequestContext context) {
        Object value = context == null ? null : context.getProperty("ghbam.user");
        AuthenticatedUser usuario = value instanceof AuthenticatedUser user ? user : null;
        try {
            return Response.ok(news2Service.internadosDaUnidade(usuario == null ? null : usuario.unidade())).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("plantao/passagem")
    public Response registrarPassagemPlantao(PassagemPlantaoRequest request, @Context ContainerRequestContext context) {
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(plantaoService.registrarPassagem(request, usuarioDaSessao(context))).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("plantao/passagem/{id}/receber")
    public Response receberPassagemPlantao(@PathParam("id") long id, @Context ContainerRequestContext context) {
        try {
            return Response.ok(plantaoService.receberPassagem(id, usuarioDaSessao(context))).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @GET
    @Path("plantao/passagens")
    public Response passagensDoDia(@jakarta.ws.rs.QueryParam("data") String data, @Context ContainerRequestContext context) {
        try {
            return Response.ok(plantaoService.passagensDoDia(data, usuarioDaSessao(context))).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("plantao/dispositivo")
    public Response inserirDispositivo(DispositivoInvasivoRequest request, @Context ContainerRequestContext context) {
        return executar(() -> Response.status(Response.Status.CREATED)
                .entity(dispositivoService.inserir(request, usuarioDaSessao(context))).build());
    }

    @POST
    @Path("plantao/dispositivo/{id}/retirar")
    public Response retirarDispositivo(@PathParam("id") long id, Map<String, String> corpo,
            @Context ContainerRequestContext context) {
        String motivo = corpo == null ? null : corpo.get("motivo");
        return executar(() -> Response.ok(dispositivoService.retirar(id, motivo, usuarioDaSessao(context))).build());
    }

    @GET
    @Path("plantao/dispositivos/indicadores")
    public Response indicadoresDispositivos(@jakarta.ws.rs.QueryParam("inicio") String inicio,
            @jakarta.ws.rs.QueryParam("fim") String fim, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(dispositivoService.indicadores(inicio, fim, usuarioDaSessao(context))).build());
    }

    @GET
    @Path("indicadores-seguranca")
    public Response indicadoresSeguranca(@jakarta.ws.rs.QueryParam("inicio") String inicio,
            @jakarta.ws.rs.QueryParam("fim") String fim, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(indicadoresService.montar(inicio, fim, usuarioDaSessao(context))).build());
    }

    @GET
    @Path("regras-dose/gestao")
    public Response gestaoRegrasDose() {
        return Response.ok(regraDoseService.listar()).build();
    }

    @GET
    @Path("regras-dose/historico")
    public Response historicoRegraDose(@jakarta.ws.rs.QueryParam("nome") String nome) {
        return Response.ok(regraDoseService.historico(nome)).build();
    }

    @POST
    @Path("regras-dose/editar")
    public Response editarRegraDose(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(regraDoseService.salvar(corpo == null ? Map.of() : corpo,
                usuarioDaSessao(context))).build());
    }

    @POST
    @Path("regras-dose/sugerir")
    public Response sugerirRegraDose(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.status(Response.Status.CREATED)
                .entity(regraDoseService.sugerir(corpo == null ? Map.of() : corpo, usuarioDaSessao(context))).build());
    }

    @GET
    @Path("regras-dose/sugestoes")
    public Response sugestoesRegraDose(@jakarta.ws.rs.QueryParam("status") String status) {
        return Response.ok(regraDoseService.sugestoes(status)).build();
    }

    @POST
    @Path("regras-dose/sugestoes/decidir")
    public Response decidirSugestaoRegraDose(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(regraDoseService.decidirSugestao(corpo == null ? Map.of() : corpo,
                usuarioDaSessao(context))).build());
    }

    @POST
    @Path("regras-dose/incluir")
    public Response incluirRegraDose(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.status(Response.Status.CREATED)
                .entity(regraDoseService.incluir(corpo == null ? Map.of() : corpo, usuarioDaSessao(context))).build());
    }

    @POST
    @Path("regras-dose/excluir")
    public Response excluirRegraDose(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(regraDoseService.excluir(corpo == null ? Map.of() : corpo,
                usuarioDaSessao(context))).build());
    }

    @POST
    @Path("regras-dose/restaurar")
    public Response restaurarRegraDose(Map<String, Object> corpo, @Context ContainerRequestContext context) {
        return executar(() -> Response.ok(regraDoseService.restaurar(corpo == null ? Map.of() : corpo,
                usuarioDaSessao(context))).build());
    }

    private Response executar(java.util.function.Supplier<Response> acao) {
        try {
            return acao.get();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    private AuthenticatedUser usuarioDaSessao(ContainerRequestContext context) {
        Object value = context == null ? null : context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser user ? user : null;
    }

    @GET
    @Path("regras-dose")
    public Response regrasDose() {
        return Response.ok(regrasBaseFarmaceutica.regrasDose()).build();
    }

    @GET
    @Path("reconciliacao/bam/{idbam}/paciente/{idpaciente}")
    public Response listarReconciliacao(@PathParam("idbam") int idbam, @PathParam("idpaciente") int idpaciente) {
        return Response.ok(reconciliacaoService.listar(idbam, idpaciente)).build();
    }

    @POST
    @Path("reconciliacao/decisao")
    public Response decidirReconciliacao(ReconciliacaoDecisaoRequest request, @Context ContainerRequestContext context) {
        Object value = context == null ? null : context.getProperty("ghbam.user");
        AuthenticatedUser usuario = value instanceof AuthenticatedUser user ? user : null;
        try {
            return Response.ok(reconciliacaoService.decidir(request, usuario)).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }

    private void preencherUsuarioAutenticado(CdssOverrideRequest request, ContainerRequestContext context) {
        if (request == null || context == null) return;
        Object value = context.getProperty("ghbam.user");
        if (!(value instanceof AuthenticatedUser user) || user.matricula().isBlank()) return;
        if (request.getUsuarioMatricula() == null || request.getUsuarioMatricula().isBlank()) {
            request.setUsuarioMatricula(user.matricula());
        }
        if (request.getUsuarioNome() == null || request.getUsuarioNome().isBlank()) {
            request.setUsuarioNome(user.nome());
        }
        if (request.getUnidade() == null || request.getUnidade().isBlank()) {
            request.setUnidade(user.unidade());
        }
    }
}
