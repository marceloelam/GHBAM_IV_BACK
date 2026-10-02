package br.com.iv.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.iv.entity.BalancoHidricoAnotacaoEnfermagem;
import br.com.iv.entity.BalancoHidricoAuditoria;
import br.com.iv.entity.BalancoHidricoEntrada;
import br.com.iv.entity.BalancoHidricoPendencia;
import br.com.iv.entity.BalancoHidricoPlantao;
import br.com.iv.entity.BalancoHidricoSaida;
import br.com.iv.entity.BalancoHidricoTransporteSaldo;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("backend_balanco_hidrico_completo")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BalancoHidricoCompletoController {

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "ldap.admin.password")
    Optional<String> senhaAdminConfigurada;

    @GET
    @Path("/prontuario/{bam}/dt/{data}")
    public Response buscarProntuario(@PathParam("bam") String bam, @PathParam("data") String dataTexto) {
        LocalDate data = parseData(dataTexto);
        if (isBlank(bam) || data == null) return badRequest("BAM e data sao obrigatorios.");

        Map<String, Object> payload = new HashMap<>();
        payload.put("bam", bam);
        payload.put("data", data.toString());
        payload.put("transportesSaldo", BalancoHidricoTransporteSaldo.list("bam = ?1 and data = ?2 order by criadoEm desc", bam, data));
        payload.put("anotacoesEnfermagem", anotacaoDoDia(bam, data));
        payload.put("pendencias", pendenciasDto(bam, data));
        payload.put("plantoes", BalancoHidricoPlantao.list("bam = ?1 and data = ?2 order by criadoEm desc", bam, data));
        payload.put("eventos", BalancoHidricoAuditoria.list("bam = ?1 and data = ?2 order by criadoEm desc", bam, data));
        Map<String, Object> saldoAnterior = saldoAnterior(bam, data);
        Map<String, Object> saldos = saldosDoPeriodo(bam, data, saldoAnterior);
        payload.put("saldoAnteriorTransportado", saldoAnterior);
        payload.put("saldos", saldos);
        payload.put("saldoAnteriorMl", saldos.get("saldoAnteriorMl"));
        payload.put("saldoDiaMl", saldos.get("saldoDiaMl"));
        payload.put("saldoAcumuladoMl", saldos.get("saldoAcumuladoMl"));
        return Response.ok(payload).build();
    }

    @POST
    @Path("/anotacoes-enfermagem")
    @Transactional
    public Response criarAnotacao(BalancoHidricoAnotacaoEnfermagem dto) {
        Response erro = validarBase(dto.bam, dto.data);
        if (erro != null) return erro;
        if (!isBlank(dto.uuid) && BalancoHidricoAnotacaoEnfermagem.findById(dto.uuid) != null) return atualizarAnotacao(dto);
        dto.persist();
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @PUT
    @Path("/anotacoes-enfermagem")
    @Transactional
    public Response atualizarAnotacao(BalancoHidricoAnotacaoEnfermagem dto) {
        Response erro = validarBase(dto.bam, dto.data);
        if (erro != null) return erro;
        BalancoHidricoAnotacaoEnfermagem entity = !isBlank(dto.uuid) ? BalancoHidricoAnotacaoEnfermagem.findById(dto.uuid) : null;
        if (entity == null) entity = anotacaoDoDia(dto.bam, dto.data);
        if (entity == null) {
            dto.persist();
            return Response.status(Response.Status.CREATED).entity(dto).build();
        }
        entity.diurno = dto.diurno;
        entity.noturno = dto.noturno;
        entity.responsavelDiurno = dto.responsavelDiurno;
        entity.matriculaDiurno = dto.matriculaDiurno;
        entity.responsavelNoturno = dto.responsavelNoturno;
        entity.matriculaNoturno = dto.matriculaNoturno;
        entity.unidade = dto.unidade;
        return Response.ok(entity).build();
    }

    @POST
    @Path("/transportes-saldo")
    @Transactional
    public Response criarTransporteSaldo(BalancoHidricoTransporteSaldo dto) {
        Response erro = validarBase(dto.bam, dto.data);
        if (erro != null) return erro;
        dto.persist();
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @PUT
    @Path("/transportes-saldo")
    @Transactional
    public Response atualizarTransporteSaldo(BalancoHidricoTransporteSaldo dto) {
        BalancoHidricoTransporteSaldo entity = !isBlank(dto.uuid) ? BalancoHidricoTransporteSaldo.findById(dto.uuid) : null;
        if (entity == null) return criarTransporteSaldo(dto);
        entity.periodo = dto.periodo;
        entity.turno = dto.turno;
        entity.hora = dto.hora;
        entity.inicioPeriodo = dto.inicioPeriodo;
        entity.fimPeriodo = dto.fimPeriodo;
        entity.totalEntradasMl = dto.totalEntradasMl;
        entity.totalSaidasMl = dto.totalSaidasMl;
        entity.saldoPeriodoMl = dto.saldoPeriodoMl;
        entity.saldoAnteriorMl = dto.saldoAnteriorMl;
        entity.saldoAcumuladoMl = dto.saldoAcumuladoMl;
        entity.responsavel = dto.responsavel;
        entity.matricula = dto.matricula;
        entity.observacao = dto.observacao;
        return Response.ok(entity).build();
    }

    @POST
    @Path("/pendencias")
    @Transactional
    public Response criarPendencia(PendenciaDto dto) {
        Response erro = validarBase(dto.bam, dto.data);
        if (erro != null) return erro;
        BalancoHidricoPendencia entity = toPendenciaEntity(new BalancoHidricoPendencia(), dto);
        entity.persist();
        return Response.status(Response.Status.CREATED).entity(toPendenciaDto(entity)).build();
    }

    @PUT
    @Path("/pendencias")
    @Transactional
    public Response atualizarPendencia(PendenciaDto dto) {
        BalancoHidricoPendencia entity = !isBlank(dto.uuid) ? BalancoHidricoPendencia.findById(dto.uuid) : null;
        if (entity == null) return criarPendencia(dto);
        toPendenciaEntity(entity, dto);
        return Response.ok(toPendenciaDto(entity)).build();
    }

    @POST
    @Path("/plantoes")
    @Transactional
    public Response criarPlantao(BalancoHidricoPlantao dto) {
        Response erro = validarBase(dto.bam, dto.data);
        if (erro != null) return erro;
        dto.persist();
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @POST
    @Path("/plantoes/desbloquear")
    @Transactional
    public Response desbloquearPlantao(DesbloqueioDto dto) {
        if (isBlank(dto.bam) || dto.data == null || isBlank(dto.plantaoUuid) || isBlank(dto.senhaAdm)) {
            return badRequest("BAM, data, fechamento e senha administrativa sao obrigatorios.");
        }
        if (senhaAdminConfigurada.isPresent()
            && !senhaAdminConfigurada.get().isBlank()
            && !senhaAdminConfigurada.get().equals(dto.senhaAdm)) {
            return Response.status(Response.Status.FORBIDDEN).entity(erro("Senha administrativa invalida.")).build();
        }
        BalancoHidricoPlantao plantao = BalancoHidricoPlantao.findById(dto.plantaoUuid);
        if (plantao == null) return Response.status(Response.Status.NOT_FOUND).entity(erro("Fechamento nao encontrado.")).build();
        plantao.statusFechamento = "DESBLOQUEADO";
        plantao.desbloqueadoEm = LocalDateTime.now();
        plantao.desbloqueadoPor = dto.profissional;
        plantao.justificativaDesbloqueio = dto.justificativa;

        BalancoHidricoAuditoria evento = new BalancoHidricoAuditoria();
        evento.bam = dto.bam;
        evento.data = dto.data;
        evento.tipo = "DESBLOQUEIO_PLANTAO";
        evento.descricao = dto.justificativa;
        evento.profissional = dto.profissional;
        evento.matricula = dto.matricula;
        evento.destino = dto.destino;
        evento.persist();

        Map<String, Object> payload = new HashMap<>();
        payload.put("plantoes", BalancoHidricoPlantao.list("bam = ?1 and data = ?2 order by criadoEm desc", dto.bam, dto.data));
        payload.put("eventos", BalancoHidricoAuditoria.list("bam = ?1 and data = ?2 order by criadoEm desc", dto.bam, dto.data));
        return Response.ok(payload).build();
    }

    @POST
    @Path("/auditoria")
    @Transactional
    public Response registrarAuditoria(BalancoHidricoAuditoria dto) {
        Response erro = validarBase(dto.bam, dto.data);
        if (erro != null) return erro;
        dto.persist();
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    private BalancoHidricoAnotacaoEnfermagem anotacaoDoDia(String bam, LocalDate data) {
        return BalancoHidricoAnotacaoEnfermagem.find("bam = ?1 and data = ?2 order by atualizadoEm desc", bam, data).firstResult();
    }

    private List<Map<String, Object>> pendenciasDto(String bam, LocalDate data) {
        List<BalancoHidricoPendencia> pendencias = BalancoHidricoPendencia.list("bam = ?1 and data = ?2 order by criadoEm desc", bam, data);
        return pendencias.stream().map(this::toPendenciaDto).toList();
    }

    private Map<String, Object> saldoAnterior(String bam, LocalDate data) {
        BalancoHidricoPlantao plantao = BalancoHidricoPlantao
            .find("bam = ?1 and data < ?2 and saldoTransportadoMl is not null and (statusFechamento is null or statusFechamento <> 'DESBLOQUEADO') order by data desc, criadoEm desc", bam, data)
            .firstResult();
        if (plantao != null) return saldoPayload(plantao.saldoTransportadoMl, plantao.data, plantao.uuid);

        BalancoHidricoTransporteSaldo transporte = BalancoHidricoTransporteSaldo
            .find("bam = ?1 and data < ?2 and saldoAcumuladoMl is not null order by data desc, criadoEm desc", bam, data)
            .firstResult();
        if (transporte != null) return saldoPayload(transporte.saldoAcumuladoMl, transporte.data, transporte.uuid);

        LocalDate dataAnterior = data.minusDays(1);
        if (temLancamentoNoDia(bam, dataAnterior)) {
            return saldoPayload(saldoDoDia(bam, dataAnterior), dataAnterior, null);
        }
        return saldoPayload(0d, null, null);
    }

    private Map<String, Object> saldoPayload(Double valor, LocalDate dataOrigem, String uuidOrigem) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("valorMl", valor == null ? 0d : valor);
        payload.put("dataOrigem", dataOrigem == null ? "" : dataOrigem.toString());
        payload.put("uuidOrigem", uuidOrigem == null ? "" : uuidOrigem);
        return payload;
    }

    private Map<String, Object> saldosDoPeriodo(String bam, LocalDate data, Map<String, Object> saldoAnterior) {
        double anterior = asDouble(saldoAnterior.get("valorMl"));
        double dia = saldoDoDia(bam, data);
        Map<String, Object> payload = new HashMap<>();
        payload.put("saldoAnteriorMl", anterior);
        payload.put("saldoDiaMl", dia);
        payload.put("saldoAcumuladoMl", anterior + dia);
        return payload;
    }

    private double saldoDoDia(String bam, LocalDate data) {
        double entradas = somarEntradas(bam, data);
        double saidas = somarSaidas(bam, data);
        return entradas - saidas;
    }

    private boolean temLancamentoNoDia(String bam, LocalDate data) {
        long entradas = BalancoHidricoEntrada.count(
            "bam = ?1 AND cast(dataRegistro as date) = cast(?2 as date)",
            bam,
            data.toString());
        long saidas = BalancoHidricoSaida.count(
            "bam = ?1 AND cast(dataRegistro as date) = cast(?2 as date)",
            bam,
            data.toString());
        return entradas > 0 || saidas > 0;
    }

    private double somarEntradas(String bam, LocalDate data) {
        return BalancoHidricoEntrada.<BalancoHidricoEntrada>list(
                "bam = ?1 AND cast(dataRegistro as date) = cast(?2 as date)",
                bam,
                data.toString())
            .stream()
            .mapToDouble(item -> item.getVolumeMl())
            .sum();
    }

    private double somarSaidas(String bam, LocalDate data) {
        return BalancoHidricoSaida.<BalancoHidricoSaida>list(
                "bam = ?1 AND cast(dataRegistro as date) = cast(?2 as date)",
                bam,
                data.toString())
            .stream()
            .mapToDouble(item -> item.getVolumeMl())
            .sum();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? 0d : Double.parseDouble(String.valueOf(value).replace(",", "."));
        } catch (Exception e) {
            return 0d;
        }
    }

    private BalancoHidricoPendencia toPendenciaEntity(BalancoHidricoPendencia entity, PendenciaDto dto) {
        entity.uuid = dto.uuid;
        entity.bam = dto.bam;
        entity.data = dto.data;
        entity.prioridade = dto.prioridade;
        entity.status = dto.status;
        entity.destinatario = dto.destinatario;
        entity.descricao = dto.descricao;
        entity.responsavel = dto.responsavel;
        entity.matricula = dto.matricula;
        entity.turno = dto.turno;
        entity.ultimaResposta = dto.ultimaResposta;
        entity.respondidoPor = dto.respondidoPor;
        entity.resolvidoPor = dto.resolvidoPor;
        entity.resolvidoEm = dto.resolvidoEm;
        entity.respostasJson = writeJson(dto.respostas);
        return entity;
    }

    private Map<String, Object> toPendenciaDto(BalancoHidricoPendencia entity) {
        Map<String, Object> dto = mapper.convertValue(entity, new TypeReference<Map<String, Object>>() {});
        dto.remove("respostasJson");
        dto.put("respostas", readJsonList(entity.respostasJson));
        return dto;
    }

    private String writeJson(Object value) {
        try {
            return value == null ? "[]" : mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> readJsonList(String value) {
        try {
            if (isBlank(value)) return Collections.emptyList();
            return mapper.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Response validarBase(String bam, LocalDate data) {
        if (isBlank(bam) || data == null) return badRequest("BAM e data sao obrigatorios.");
        return null;
    }

    private LocalDate parseData(String data) {
        try {
            return isBlank(data) ? null : LocalDate.parse(data.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Response badRequest(String mensagem) {
        return Response.status(Response.Status.BAD_REQUEST).entity(erro(mensagem)).build();
    }

    private Map<String, String> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("message", mensagem);
        return erro;
    }

    public static class PendenciaDto {
        public String uuid;
        public String bam;
        public LocalDate data;
        public String prioridade;
        public String status;
        public String destinatario;
        public String descricao;
        public String responsavel;
        public String matricula;
        public String turno;
        public List<Map<String, Object>> respostas;
        public String ultimaResposta;
        public String respondidoPor;
        public String resolvidoPor;
        public LocalDateTime resolvidoEm;
    }

    public static class DesbloqueioDto {
        public String bam;
        public LocalDate data;
        public String plantaoUuid;
        public String senhaAdm;
        public String justificativa;
        public String profissional;
        public String matricula;
        public String destino;
    }
}
