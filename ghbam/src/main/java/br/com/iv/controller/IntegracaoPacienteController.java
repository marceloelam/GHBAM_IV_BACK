package br.com.iv.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import br.com.iv.dto.LogDto;
import br.com.iv.security.AuditPacienteResolver;
import br.com.iv.service.IntegracaoPacienteService;
import br.com.iv.service.IntegracaoPacienteService.Filtro;
import br.com.iv.service.IntegracaoPacienteService.Integrador;
import br.com.iv.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Consulta de paciente para sistemas externos, sem sessao GHBAM: autenticacao pelo header
 * X-API-Key (ver IntegracaoPacienteService). A rota e liberada no RbacRequestFilter
 * justamente porque a chave e validada aqui - nenhuma resposta sai sem ela conferir.
 *
 * GET  /integracao/pacientes?cpf=&cns=&nome=&nascimento=&mae=
 * POST /integracao/pacientes  corpo JSON {"cpf":"...","nome":"...","nascimento":"...","mae":"...","cns":"..."}
 * Aceita qualquer combinacao: cpf ou cns sozinhos, ou ao menos dois entre nome,
 * nascimento e mae. Toda consulta vira linha em /logs (sem CPF/nome no texto).
 */
@Path("integracao/pacientes")
@Produces(MediaType.APPLICATION_JSON)
public class IntegracaoPacienteController {

    private static final Logger LOG = Logger.getLogger(IntegracaoPacienteController.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    IntegracaoPacienteService service;

    @Inject
    LogService logService;

    @GET
    public Response consultar(@HeaderParam("X-API-Key") String chave,
            @QueryParam("nome") String nome,
            @QueryParam("nascimento") String nascimento,
            @QueryParam("mae") String mae,
            @QueryParam("cpf") String cpf,
            @QueryParam("cns") String cns,
            @Context ContainerRequestContext context) {
        return executar(chave, new Filtro(nome, nascimento, mae, cpf, cns), context);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response consultarPorCorpo(@HeaderParam("X-API-Key") String chave, Filtro corpo,
            @Context ContainerRequestContext context) {
        return executar(chave, corpo == null ? new Filtro(null, null, null, null, null) : corpo, context);
    }

    private Response executar(String chave, Filtro filtro, ContainerRequestContext context) {
        Optional<Integrador> integrador = service.autenticar(chave);
        if (integrador.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("mensagem", "Chave de API ausente ou invalida (header X-API-Key)."))
                    .build();
        }

        String erro = IntegracaoPacienteService.validar(filtro);
        if (erro != null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("mensagem", erro)).build();
        }

        try {
            Map<String, Object> resposta = service.consultar(filtro, integrador.get());
            auditar(integrador.get(), filtro, resposta, context);
            return Response.ok(resposta).build();
        } catch (Exception e) {
            LOG.error("Falha na consulta de paciente por integracao (" + integrador.get().sistema() + ")", e);
            return Response.serverError()
                    .entity(Map.of("mensagem", "Nao foi possivel consultar o paciente agora."))
                    .build();
        }
    }

    private void auditar(Integrador integrador, Filtro f, Map<String, Object> resposta,
            ContainerRequestContext context) {
        try {
            LogDto dto = new LogDto();
            dto.setData(LocalDate.now());
            dto.setHora(LocalTime.now().format(HORA));
            // Registra quem consultou, por quais campos e o resultado - nunca os valores
            // (CPF, nome), que ja estao no cadastro e nao precisam ser duplicados no log.
            dto.setEvento(String.format("INTEGRACAO API sistema=%s consulta paciente campos=%s origem=%s total=%s ip=%s",
                    integrador.sistema(), campos(f), resposta.get("origem"), resposta.get("total"), ip(context)));
            dto.setUnidade(integrador.unidade().length() > 20 ? integrador.unidade().substring(0, 20) : integrador.unidade());
            Object pacientes = resposta.get("pacientes");
            if (pacientes instanceof List<?> lista && lista.size() == 1
                    && lista.get(0) instanceof Map<?, ?> p && p.get("idpaciente") != null) {
                dto.setPacienteTipo(AuditPacienteResolver.TIPO_ID_PACIENTE);
                dto.setPacienteValor(String.valueOf(p.get("idpaciente")));
            }
            logService.salvarLogAsync(dto);
        } catch (Exception e) {
            LOG.warn("Falha ao auditar consulta de integracao: " + e.getMessage());
        }
    }

    private static String campos(Filtro f) {
        StringBuilder s = new StringBuilder();
        if (f.cpf() != null && !f.cpf().isBlank()) s.append("cpf,");
        if (f.cns() != null && !f.cns().isBlank()) s.append("cns,");
        if (f.nome() != null && !f.nome().isBlank()) s.append("nome,");
        if (f.nascimento() != null && !f.nascimento().isBlank()) s.append("nascimento,");
        if (f.mae() != null && !f.mae().isBlank()) s.append("mae,");
        return s.length() == 0 ? "-" : s.substring(0, s.length() - 1);
    }

    private static String ip(ContainerRequestContext context) {
        String encaminhado = context.getHeaderString("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        String real = context.getHeaderString("X-Real-IP");
        return real == null || real.isBlank() ? "-" : real.trim();
    }
}
