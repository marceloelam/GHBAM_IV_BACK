package br.com.iv.controller;

import br.com.iv.dto.CadsusPdqConsultaDto;
import br.com.iv.dto.IntegracaoSusCredencialDto;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.CadsusPdqClienteService;
import br.com.iv.service.ConfiguracaoCnsPdqService;
import br.com.iv.service.IntegracaoSusCredencialService;
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
import java.util.List;
import java.util.Map;

@Path("integracao-sus")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IntegracaoSusController {

    @Inject
    IntegracaoSusCredencialService service;

    @Inject
    CadsusPdqClienteService cadsusPdqService;

    @Inject
    ConfiguracaoCnsPdqService configuracaoCnsPdqService;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @GET
    @Path("/credenciais/unidade/{unidade}")
    public Response buscar(@PathParam("unidade") String unidade) {
        return service.buscar(unidade)
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).entity("Credencial SUS nao configurada.").build());
    }

    @POST
    @Path("/credenciais")
    public Response salvar(IntegracaoSusCredencialDto dto, @Context ContainerRequestContext context) {
        try {
            return Response.ok(service.salvar(dto, user(context))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/testar/unidade/{unidade}")
    public Response testar(@PathParam("unidade") String unidade, @Context ContainerRequestContext context) {
        return Response.ok(service.testar(unidade, user(context), cadsusPdqService::testarConexao)).build();
    }

    /**
     * Consulta oficial ao CADSUS/CNS (PDQ) usando o certificado ja cadastrado na unidade.
     * Nunca inventa CNS: se a credencial nao existir/estiver incompleta, ou o DATASUS
     * nao encontrar o paciente, o corpo de resposta explica o motivo (ver CadsusPdqRespostaDto).
     */
    @POST
    @Path("/cns/consultar/unidade/{unidade}")
    public Response consultarCns(@PathParam("unidade") String unidade, CadsusPdqConsultaDto filtro) {
        return Response.ok(cadsusPdqService.consultar(unidade, filtro)).build();
    }

    /**
     * Toggle POR UNIDADE da consulta oficial do CNS/PDQ na /recepcao. Enquanto a unidade
     * nao estiver ativada (estado inicial), ela usa so o scraping do CNES - nenhum
     * comportamento existente muda ate isto ser ligado deliberadamente para aquela unidade.
     */
    @GET
    @Path("/cns/oficial/unidade/{unidade}")
    public Response consultarStatusOficial(@PathParam("unidade") String unidade) {
        return Response.ok(Map.of("ativo", configuracaoCnsPdqService.ativo(unidade))).build();
    }

    @POST
    @Path("/cns/oficial/unidade/{unidade}")
    public Response definirStatusOficial(@PathParam("unidade") String unidade, Map<String, Object> dto,
            @Context ContainerRequestContext context) {
        boolean ativo = Boolean.TRUE.equals(dto == null ? null : dto.get("ativo"));
        // So pode ATIVAR com a credencial em PRODUCAO - homologacao e uma base separada e
        // reduzida do DATASUS (sem pacientes reais), e um "nao encontrado" vindo de la nao
        // pode ser confundido com a resposta do CADSUS de verdade. Validado tambem aqui (nao
        // so no frontend) porque e regra de seguranca, nao so de UX.
        if (ativo && !credencialEmProducao(unidade)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "A credencial desta unidade nao esta em ambiente de PRODUCAO. "
                            + "Configure o ambiente de producao antes de ativar a consulta oficial."))
                    .build();
        }
        boolean valorSalvo = configuracaoCnsPdqService.definir(unidade, ativo, user(context));
        return Response.ok(Map.of("ativo", valorSalvo)).build();
    }

    private boolean credencialEmProducao(String unidade) {
        return service.buscar(unidade)
                .map(credencial -> "PRODUCAO".equalsIgnoreCase(credencial.getAmbiente()))
                .orElse(false);
    }

    /** Status da consulta oficial em TODAS as unidades cadastradas, para a tela listar de uma vez. */
    @GET
    @Path("/cns/oficial/status-todas")
    public Response consultarStatusTodasUnidades() {
        return Response.ok(configuracaoCnsPdqService.statusPorUnidade(siglasDasUnidades())).build();
    }

    /**
     * Atalho para ativar/desativar em todas as unidades cadastradas de uma vez - util
     * quando a maioria ja foi validada e nao faz mais sentido religar uma por uma.
     */
    @POST
    @Path("/cns/oficial/todas")
    public Response definirStatusTodasUnidades(Map<String, Object> dto, @Context ContainerRequestContext context) {
        boolean ativo = Boolean.TRUE.equals(dto == null ? null : dto.get("ativo"));
        // Desativar vale para todas sem restricao. Ativar so entra quem tem credencial em
        // PRODUCAO - as demais ficam de fora (mesma regra de definirStatusOficial), sem
        // travar as que ja estao prontas por causa das que ainda estao em homologacao.
        List<String> unidades = siglasDasUnidades();
        List<String> alvo = ativo
                ? unidades.stream().filter(this::credencialEmProducao).toList()
                : unidades;
        var resultado = configuracaoCnsPdqService.definirParaTodas(alvo, ativo, user(context));
        List<String> puladasSemProducao = ativo
                ? unidades.stream().filter(u -> !alvo.contains(u)).toList()
                : List.of();
        return Response.ok(Map.of("resultado", resultado, "puladasSemProducao", puladasSemProducao)).build();
    }

    private List<String> siglasDasUnidades() {
        return configuracaoUnidadeRepository.listAll().stream()
                .map(ConfiguracaoUnidade::getSigla)
                .filter(sigla -> sigla != null && !sigla.isBlank())
                .map(sigla -> sigla.trim().toUpperCase())
                .distinct()
                .toList();
    }

    @POST
    @Path("/auditoria/evento")
    public Response registrarEvento(Map<String, Object> dto, @Context ContainerRequestContext context) {
        service.registrarEvento(dto, user(context));
        return Response.ok(Map.of("ok", true)).build();
    }

    @GET
    @Path("/auditoria/unidade/{unidade}")
    public Response auditoria(@PathParam("unidade") String unidade) {
        return Response.ok(service.auditoria(unidade)).build();
    }

    private AuthenticatedUser user(ContainerRequestContext context) {
        Object value = context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : AuthenticatedUser.empty();
    }
}
