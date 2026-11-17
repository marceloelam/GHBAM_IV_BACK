package br.com.iv.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.dto.FhirEnvioFilaStatusDto;
import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.entity.Bam;
import br.com.iv.entity.FhirEnvioDestino;
import br.com.iv.entity.FhirEnvioFila;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.event.FhirEnvioSolicitadoEvent;
import br.com.iv.security.AuthenticatedUser;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import io.quarkus.scheduler.Scheduled;

// Motor de envio de sumarios clinicos em FHIR. O enfileiramento e sincrono e
// barato (so grava PENDENTE); a tentativa de rede acontece depois do commit
// da transacao de origem (assinatura clinica), para que uma falha de rede
// jamais desfaca uma assinatura ja confirmada pelo profissional. Ver decisao
// 1 do plano de implementacao (RISAH/FHIR do resumo de alta).
@ApplicationScoped
@Startup
public class FhirDispatchService {

    private static final Logger LOG = Logger.getLogger(FhirDispatchService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @ConfigProperty(name = "ghbam.fhir.dispatch.max-tentativas", defaultValue = "10")
    int maxTentativas;

    @Inject
    EntityManager entityManager;

    @Inject
    FhirEnvioDestinoService destinoService;

    @Inject
    FhirSumarioAltaMapperService mapperService;

    @Inject
    FhirResultadoLaboratorioMapperService laboratorioMapperService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Event<FhirEnvioSolicitadoEvent> envioEvent;

    @Inject
    MeterRegistry registry;

    @Inject
    FhirFilaMetricsService filaMetricsService;

    @Inject
    FhirR4PayloadValidator payloadValidator;

    @Inject
    CadsusPdqClienteService cadsusPdqClienteService;

    static final String TIPO_RNDS = "RNDS_CERTIFICADO";

    // Resultado de exame laboratorial: tipo na fila e finalidade do destino tem o mesmo nome.
    public static final String TIPO_RESULTADO_EXAME = "RESULTADO_EXAME";

    // Expoe o tamanho da fila FHIR por status em /q/metrics (ghbam_fhir_fila_documentos{status=...}).
    // A leitura de cada status roda via FhirFilaMetricsService (bean separado, nao "this") porque o
    // Gauge chama esse supplier fora de qualquer transacao/requisicao HTTP, e so um proxy CDI de
    // outro bean aciona o @Transactional necessario para o Panache consultar o banco.
    @PostConstruct
    void registrarMetricasFila() {
        for (String status : List.of("PENDENTE", "ERRO", "ERRO_DEFINITIVO")) {
            Gauge.builder("ghbam_fhir_fila_documentos", filaMetricsService, s -> (double) s.contarPorStatus(status))
                    .description("Documentos na fila de envio FHIR do sumario de alta, por status")
                    .tag("status", status)
                    .register(registry);
        }
    }

    // Chamado de dentro de AssinaturaClinicaService.assinar(), na mesma
    // transacao. Defensivo de proposito: nunca deve derrubar a assinatura.
    @Transactional(Transactional.TxType.MANDATORY)
    public void enfileirarAposAssinatura(AssinaturaClinica assinatura) {
        try {
            Long idFila = inserirNaFila(assinatura);
            if (idFila != null) {
                envioEvent.fire(new FhirEnvioSolicitadoEvent(idFila));
            }
        } catch (Exception e) {
            LOG.errorf(e, "[FHIR-DISPATCH] falha ao enfileirar assinatura=%s", assinatura.getIdassinatura());
        }
    }

    // Chamado ao salvar um resultado laboratorial com dados RNDS, na mesma transacao.
    // Defensivo como enfileirarAposAssinatura: nunca derruba o salvamento do resultado.
    @Transactional(Transactional.TxType.MANDATORY)
    public void enfileirarResultadoLaboratorial(PedidoResultadoLaboratorio resultado) {
        try {
            // Ja na fila: dados completados depois recolocam como PENDENTE; o que ja foi
            // ENVIADO nao e reenviado automaticamente (seria outro documento na RNDS).
            FhirEnvioFila existente = FhirEnvioFila.<FhirEnvioFila>find("tipoDocumento = ?1 and iddocumento = ?2",
                    TIPO_RESULTADO_EXAME, resultado.getIdPedidoResultado()).firstResult();
            if (existente != null) {
                if (!"ENVIADO".equals(existente.getStatus())) {
                    zerarTentativas(existente);
                    existente.setRespostaHttp(null);
                    existente.setAtualizadoEm(LocalDateTime.now());
                    envioEvent.fire(new FhirEnvioSolicitadoEvent(existente.getIdfhirenviofila()));
                }
                return;
            }
            Bam bam = Bam.findById(resultado.getBam());
            FhirEnvioFila fila = new FhirEnvioFila();
            fila.setTipoDocumento(TIPO_RESULTADO_EXAME);
            fila.setIddocumento(resultado.getIdPedidoResultado());
            fila.setIdbam(resultado.getBam());
            fila.setIdpaciente(bam == null || bam.getPaciente() == null ? null : bam.getPaciente().getIdpaciente());
            String unidade = resultado.getUnidade() != null ? resultado.getUnidade() : bam == null ? null : bam.getUnidade();
            fila.setUnidade(unidade == null ? "" : unidade);
            fila.setStatus("PENDENTE");
            fila.setTentativas(0);
            fila.setCriadoEm(LocalDateTime.now());
            fila.persist();
            entityManager.flush();
            envioEvent.fire(new FhirEnvioSolicitadoEvent(fila.getIdfhirenviofila()));
        } catch (Exception e) {
            LOG.errorf(e, "[FHIR-DISPATCH] falha ao enfileirar resultado laboratorial=%s", resultado.getIdPedidoResultado());
        }
    }

    static String finalidadeDestino(String tipoDocumento) {
        return "RESUMO_ALTA".equals(tipoDocumento) ? "SUMARIO_ALTA" : tipoDocumento;
    }

    private Long inserirNaFila(AssinaturaClinica assinatura) {
        FhirEnvioFila fila = new FhirEnvioFila();
        fila.setTipoDocumento(assinatura.getTipoDocumento());
        fila.setIddocumento(assinatura.getIddocumento());
        fila.setIdbam(assinatura.getIdbam());
        fila.setIdpaciente(assinatura.getIdpaciente());
        fila.setIdassinatura(assinatura.getIdassinatura());
        fila.setUnidade(assinatura.getUnidade() == null ? "" : assinatura.getUnidade());
        fila.setStatus("PENDENTE");
        fila.setTentativas(0);
        fila.setCriadoEm(LocalDateTime.now());
        fila.persist();
        entityManager.flush();
        return fila.getIdfhirenviofila();
    }

    @Transactional
    void aoConfirmarTransacao(@Observes(during = TransactionPhase.AFTER_SUCCESS) FhirEnvioSolicitadoEvent evento) {
        try {
            processarEnvio(evento.getIdFila());
        } catch (Exception e) {
            LOG.errorf(e, "[FHIR-DISPATCH] falha ao processar envio idFila=%s (retry cobre pelo job agendado)", evento.getIdFila());
        }
    }

    @Transactional
    public void processarEnvio(Long idFila) {
        FhirEnvioFila fila = FhirEnvioFila.findById(idFila);
        if (fila == null || "ENVIADO".equals(fila.getStatus())) {
            return;
        }

        String finalidade = finalidadeDestino(fila.getTipoDocumento());
        Optional<FhirEnvioDestino> destinoOpt = destinoService.buscarAtivoParaEnvio(fila.getUnidade(), finalidade);
        // Sem destino ativo: distingue "cadastrado mas desligado" de "nunca cadastrado" para o operador.
        String bloqueio = destinoOpt.isPresent() ? estadoBloqueioSemDestino(destinoOpt.get())
                : destinoService.buscar(fila.getUnidade(), finalidade).isPresent() ? "DESATIVADO" : "NAO_CONFIGURADO";
        if (bloqueio != null) {
            marcarSemTentativa(fila, bloqueio);
            return;
        }
        FhirEnvioDestino destino = destinoOpt.get();

        boolean resultadoExame = TIPO_RESULTADO_EXAME.equals(fila.getTipoDocumento());
        AssinaturaClinica assinatura = null;
        PedidoResultadoLaboratorio resultado = null;
        if (resultadoExame) {
            resultado = PedidoResultadoLaboratorio.findById(fila.getIddocumento());
            if (resultado == null) {
                marcarErroDefinitivo(fila, "Resultado laboratorial nao encontrado.");
                return;
            }
        } else {
            assinatura = fila.getIdassinatura() != null ? AssinaturaClinica.findById(fila.getIdassinatura()) : null;
            if (assinatura == null) {
                marcarSemTentativa(fila, "ERRO_DEFINITIVO");
                return;
            }
        }

        String payload;
        try {
            Map<String, Object> bundle;
            if (resultadoExame) {
                bundle = laboratorioMapperService.montarBundleResultado(resultado);
                // Pendencia de dado (LOINC, valor, amostra, CNES) nao se resolve com retry.
                List<String> pendencias = payloadValidator.avaliarResultadoLaboratorial(bundle);
                if (!pendencias.isEmpty()) {
                    marcarErroDefinitivo(fila, "Pendencias RNDS: " + String.join("; ", pendencias));
                    return;
                }
            } else {
                bundle = mapperService.montarBundleDocumento(assinatura);
                payloadValidator.validarSumarioAlta(bundle);
            }
            payload = objectMapper.writeValueAsString(bundle);
            fila.setPayloadFhir(payload);
        } catch (FhirPayloadValidationException e) {
            LOG.warnf("[FHIR-DISPATCH] payload rejeitado idFila=%s: %s", idFila, e.getMessage());
            marcarErroDefinitivo(fila, e.getMessage());
            return;
        } catch (Exception e) {
            LOG.errorf(e, "[FHIR-DISPATCH] falha ao montar payload idFila=%s", idFila);
            registrarFalha(fila, null, "Falha ao montar payload: " + e.getMessage());
            return;
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(destino.getEndpointBase()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/fhir+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            HttpClient cliente = httpClient;
            if (TIPO_RNDS.equals(destino.getTipoAutenticacao())) {
                // RNDS: token do EHR-Auth gerado com o certificado ICP-Brasil da tela
                // Integracao SUS; a conexao ao EHR tambem apresenta o certificado.
                CadsusPdqClienteService.AcessoRnds acesso;
                try {
                    acesso = cadsusPdqClienteService.acessoRnds(fila.getUnidade(), "PRODUCAO".equalsIgnoreCase(destino.getAmbiente()));
                } catch (IllegalStateException e) {
                    registrarFalha(fila, null, "Falha ao autenticar na RNDS: " + e.getMessage());
                    return;
                }
                aplicarAutenticacaoRnds(builder, acesso.token(), destinoService.credencialDecifrada(destino));
                cliente = HttpClient.newBuilder()
                        .sslContext(acesso.sslContext())
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
            } else {
                aplicarAutenticacao(builder, destino);
            }

            HttpResponse<String> response = cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            fila.setStatusHttp(response.statusCode());
            fila.setRespostaHttp(limitar(response.body(), 2000));
            String referenciaRemota = response.headers().firstValue("location")
                    .or(() -> response.headers().firstValue("content-location"))
                    .orElse(null);
            if (referenciaRemota != null && !referenciaRemota.isBlank()) {
                fila.setReferenciaRemota(limitar(referenciaRemota, 1000));
            }
            fila.setAtualizadoEm(LocalDateTime.now());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                fila.setStatus("ENVIADO");
                fila.setEnviadoEm(LocalDateTime.now());
            } else {
                // Mantem o corpo (OperationOutcome da RNDS): so "HTTP 404" nao diz o motivo.
                String corpo = response.body() == null || response.body().isBlank() ? "" : ": " + response.body();
                registrarFalha(fila, response.statusCode(), "HTTP " + response.statusCode() + corpo);
            }
        } catch (Exception e) {
            LOG.warnf("[FHIR-DISPATCH] falha de rede idFila=%s: %s", idFila, e.getMessage());
            registrarFalha(fila, null, e.getMessage());
        }
    }

    @Scheduled(every = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reprocessarPendenciasAgendado() {
        // Em cada municipio (sem requisicao o banco seria sempre o padrao).
        br.com.iv.plataforma.municipio.MunicipioRoteamento.emCadaMunicipio(FhirDispatchService.class, "reprocessarPendencias", b -> b.reprocessarPendencias());
    }

    @Transactional
    void reprocessarPendencias() {
        List<FhirEnvioFila> filas = FhirEnvioFila.find(
                "status in ('PENDENTE','ERRO') and (proximaTentativaEm is null or proximaTentativaEm <= ?1) order by criadoEm",
                LocalDateTime.now()).page(0, 20).list();
        for (FhirEnvioFila fila : filas) {
            try {
                processarEnvio(fila.getIdfhirenviofila());
            } catch (Exception e) {
                LOG.errorf(e, "[FHIR-DISPATCH] falha no retry idFila=%s", fila.getIdfhirenviofila());
            }
        }
    }

    // Estado em que o envio nao deve tentar rede: sem destino cadastrado, ou
    // destino existente porem desativado (padrao de seguranca ate haver
    // credencial real - ver plano de implementacao). Retorna null quando pode
    // prosseguir. Extraido para ser testavel sem banco.
    static String estadoBloqueioSemDestino(FhirEnvioDestino destino) {
        if (destino == null) {
            return "NAO_CONFIGURADO";
        }
        if (!Boolean.TRUE.equals(destino.getAtivo())) {
            return "DESATIVADO";
        }
        return null;
    }

    static long calcularBackoffMinutos(int tentativas) {
        return Math.min(60, (long) Math.pow(2, tentativas));
    }

    static boolean esgotouTentativas(int tentativas, int maxTentativas) {
        return tentativas >= maxTentativas;
    }

    void aplicarAutenticacao(HttpRequest.Builder builder, FhirEnvioDestino destino) {
        String credencial = destinoService.credencialDecifrada(destino);
        if (credencial == null || credencial.isBlank()) {
            return;
        }
        switch (destino.getTipoAutenticacao() == null ? "NENHUMA" : destino.getTipoAutenticacao()) {
            case "BEARER_TOKEN" -> builder.header("Authorization", "Bearer " + credencial);
            case "BASIC" -> builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credencial.getBytes()));
            case "API_KEY_HEADER" -> builder.header(
                    destino.getApiKeyHeaderNome() == null || destino.getApiKeyHeaderNome().isBlank()
                            ? "X-API-Key" : destino.getApiKeyHeaderNome(),
                    credencial);
            default -> { }
        }
    }

    // Headers exigidos pelos servicos EHR da RNDS: X-Authorization-Server leva o token
    // do EHR-Auth; Authorization leva o CNS do profissional requisitante (guardado,
    // criptografado, no campo Credencial do destino).
    static void aplicarAutenticacaoRnds(HttpRequest.Builder builder, String token, String cnsRequisitante) {
        builder.header("X-Authorization-Server", "Bearer " + token);
        if (cnsRequisitante != null && !cnsRequisitante.isBlank()) {
            builder.header("Authorization", cnsRequisitante.trim());
        }
    }

    private void marcarSemTentativa(FhirEnvioFila fila, String status) {
        fila.setStatus(status);
        fila.setAtualizadoEm(LocalDateTime.now());
    }

    private void registrarFalha(FhirEnvioFila fila, Integer statusHttp, String resposta) {
        int tentativas = (fila.getTentativas() == null ? 0 : fila.getTentativas()) + 1;
        fila.setTentativas(tentativas);
        fila.setStatusHttp(statusHttp);
        fila.setRespostaHttp(limitar(resposta, 2000));
        fila.setAtualizadoEm(LocalDateTime.now());
        if (esgotouTentativas(tentativas, maxTentativas)) {
            fila.setStatus("ERRO_DEFINITIVO");
        } else {
            fila.setStatus("ERRO");
            fila.setProximaTentativaEm(LocalDateTime.now().plusMinutes(calcularBackoffMinutos(tentativas)));
        }
    }

    private void marcarErroDefinitivo(FhirEnvioFila fila, String resposta) {
        fila.setStatus("ERRO_DEFINITIVO");
        fila.setRespostaHttp(limitar(resposta, 2000));
        fila.setAtualizadoEm(LocalDateTime.now());
    }

    // Uma transacao so: sem ela o status devolvido vinha da copia carregada antes do
    // envio (sempre PENDENTE), diferente do que a fila mostrava depois.
    @Transactional
    public FhirEnvioFilaStatusDto reenviarManual(Long idFila, AuthenticatedUser user) {
        FhirEnvioFila fila = FhirEnvioFila.findById(idFila);
        if (fila == null) {
            throw new IllegalArgumentException("Envio nao encontrado.");
        }
        zerarTentativas(fila);
        processarEnvio(idFila);
        return statusDto(FhirEnvioFila.findById(idFila));
    }

    @Transactional
    void zerarTentativas(FhirEnvioFila fila) {
        fila.setTentativas(0);
        fila.setProximaTentativaEm(null);
        fila.setStatus("PENDENTE");
    }

    public Optional<FhirEnvioFilaStatusDto> statusPorDocumento(String tipoDocumento, Integer iddocumento) {
        return FhirEnvioFila.<FhirEnvioFila>find("tipoDocumento = ?1 and iddocumento = ?2", tipoDocumento, iddocumento)
                .firstResultOptional()
                .map(this::statusDto);
    }

    @Transactional
    public void cancelarFilaPorBam(String bam) {
        if (bam == null || !bam.trim().matches("\\d+")) {
            return;
        }
        FhirEnvioFila.update("status = 'CANCELADO', proximaTentativaEm = null, atualizadoEm = ?1"
                + " where tipoDocumento = 'RESUMO_ALTA' and idbam = ?2 and status <> 'ENVIADO'",
                LocalDateTime.now(), Integer.valueOf(bam.trim()));
    }

    // Lista de envios efetivamente concluidos. Apenas ENVIADO entra nesta consulta;
    // pendencias, falhas e itens nao configurados ficam na fila operacional acima.
    // O filtro de periodo incide sobre enviadoEm, que representa a confirmacao do envio.
    public List<FhirEnvioFilaStatusDto> listarPorUnidade(String tipoDocumento, String unidade,
            LocalDateTime dataInicio, LocalDateTime dataFim) {
        StringBuilder jpql = new StringBuilder("tipoDocumento = ?1 and unidade = ?2 and status = ?3");
        List<Object> params = new java.util.ArrayList<>(List.of(tipoDocumento, unidade, "ENVIADO"));
        if (dataInicio != null) {
            params.add(dataInicio);
            jpql.append(" and enviadoEm >= ?").append(params.size());
        }
        if (dataFim != null) {
            params.add(dataFim);
            jpql.append(" and enviadoEm <= ?").append(params.size());
        }
        jpql.append(" order by enviadoEm desc");
        // .list() materializa antes de mapear - statusDto faz uma consulta a mais (nome do
        // paciente) por linha, e isso quebra em cima de .stream() porque o Hibernate ainda
        // ta com o ResultSet do find() aberto quando a consulta aninhada dispara.
        return FhirEnvioFila.<FhirEnvioFila>find(jpql.toString(), params.toArray()).list().stream()
                .map(this::statusDto)
                .toList();
    }

    // Fila operacional: todos os status (PENDENTE, ERRO, DESATIVADO, NAO_CONFIGURADO,
    // ENVIADO...), para o operador ver por que um resumo nao foi enviado. Periodo sobre
    // criadoEm (entrada na fila); unidade sem diferenciar maiusculas, como no destino.
    public List<FhirEnvioFilaStatusDto> listarFilaPorUnidade(String tipoDocumento, String unidade,
            LocalDateTime dataInicio, LocalDateTime dataFim) {
        StringBuilder jpql = new StringBuilder("tipoDocumento = ?1 and lower(unidade) = ?2");
        List<Object> params = new java.util.ArrayList<>(List.of(tipoDocumento, unidade.trim().toLowerCase(java.util.Locale.ROOT)));
        if (dataInicio != null) {
            params.add(dataInicio);
            jpql.append(" and criadoEm >= ?").append(params.size());
        }
        if (dataFim != null) {
            params.add(dataFim);
            jpql.append(" and criadoEm <= ?").append(params.size());
        }
        jpql.append(" order by criadoEm desc");
        return FhirEnvioFila.<FhirEnvioFila>find(jpql.toString(), params.toArray()).page(0, 200).list().stream()
                .map(this::statusDto)
                .toList();
    }

    private FhirEnvioFilaStatusDto statusDto(FhirEnvioFila fila) {
        if (fila == null) {
            return null;
        }
        FhirEnvioFilaStatusDto dto = new FhirEnvioFilaStatusDto();
        dto.setIdfhirenviofila(fila.getIdfhirenviofila());
        dto.setTipoDocumento(fila.getTipoDocumento());
        dto.setIddocumento(fila.getIddocumento());
        dto.setIdbam(fila.getIdbam());
        dto.setIdpaciente(fila.getIdpaciente());
        if (fila.getIdpaciente() != null && fila.getIdpaciente() > 0) {
            br.com.iv.entity.Pacientes paciente = br.com.iv.entity.Pacientes.findById(fila.getIdpaciente());
            dto.setNomePaciente(paciente == null ? "" : paciente.getNome());
        }
        dto.setUnidade(fila.getUnidade());
        dto.setStatus(fila.getStatus());
        dto.setStatusHttp(fila.getStatusHttp());
        dto.setTentativas(fila.getTentativas());
        dto.setProximaTentativaEm(fila.getProximaTentativaEm());
        dto.setCriadoEm(fila.getCriadoEm());
        dto.setEnviadoEm(fila.getEnviadoEm());
        dto.setMotivo(limitar(fila.getRespostaHttp(), 500));
        return dto;
    }

    private String limitar(String valor, int tamanho) {
        if (valor == null) return null;
        return valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }
}
