package br.com.iv.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.jboss.logging.Logger;

import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Bam;
import br.com.iv.entity.ConformidadePendenciaBaixa;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.PedidoMedico2;
import br.com.iv.entity.PrescricaoItemCicloEvento;
import br.com.iv.entity.PrescricaoItemEstruturado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ApplicationScoped
public class JornadaPaService implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Duration RESUMO_EXECUTIVO_CACHE_TTL = Duration.ofSeconds(90);
    private static final long RESUMO_EXECUTIVO_LENTO_MS = 800L;
    private static final Logger LOG = Logger.getLogger(JornadaPaService.class);

    private final Map<String, CacheEntry> resumoExecutivoCache = new ConcurrentHashMap<>();

    @Inject
    EntityManager entityManager;

    @Inject
    BamService bamService;

    @Inject
    ConformidadePendenciaBaixaService pendenciaBaixaService;

    @Inject
    ConfiguracaoConformidadeSlaService configuracaoConformidadeSlaService;

    @Inject
    PedidoMedico2Service pedidoMedico2Service;

    @Inject
    PrescricaoItemEstruturadoService prescricaoItemEstruturadoService;

    @Inject
    PrescricaoItemCicloEventoService prescricaoItemCicloEventoService;

    @Inject
    ProntuarioClinicoItemService prontuarioClinicoItemService;

    @Inject
    AssinaturaConformidadeService assinaturaConformidadeService;

    @Inject
    ConformidadePendenciaBaixaService conformidadePendenciaBaixaService;

    @Inject
    br.com.iv.cdss.service.ResultadoCriticoService resultadoCriticoService;

    @Inject
    br.com.iv.cdss.service.ReconciliacaoMedicamentosService reconciliacaoService;

    public Map<String, Object> montarPorBam(int idbam) {
        Bam bam = bamService.filtarPorUnicoBam(idbam);
        if (bam == null || bam.getIdbam() <= 0) {
            return Map.of("idbam", idbam, "encontrado", false);
        }

        List<PrescricaoItemEstruturado> prescricaoItens = prescricaoItemEstruturadoService.listarPorBam(idbam, false);
        List<PedidoMedico2> pedidosMedicos = pedidoMedico2Service.filtarBam(idbam);
        Map<String, Object> conformidade = assinaturaConformidadeService.conformidadePorBam(idbam);
        List<Map<String, Object>> eventos = listarEventos(idbam);
        eventos.addAll(listarApoioCirurgico(bam.getPaciente().getIdpaciente()));
        eventos.sort(Comparator.comparing(this::chaveOrdenacao).reversed());
        List<Map<String, Object>> faturamentoItens = listarFaturamentoBam(idbam);
        List<PrescricaoItemCicloEvento> cicloMedicacaoEventos = prescricaoItemCicloEventoService.listarPorBam(idbam);
        List<Map<String, Object>> pendenciasBaixas = pendenciaBaixaService.listarPorBam(idbam).stream()
                .map(pendenciaBaixaService::toMap)
                .toList();

        Map<String, Object> historico = new LinkedHashMap<>();
        historico.put("paciente", bam.getPaciente());
        historico.put("ultimoBam", resumoBam(bam));
        historico.put("bams", List.of(resumoBam(bam)));
        historico.put("eventos", eventos);
        historico.put("resumo", resumoEventos(eventos, prescricaoItens, pedidosMedicos));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idbam", idbam);
        response.put("encontrado", true);
        response.put("bam", bam);
        response.put("paciente", bam.getPaciente());
        response.put("historico", Map.of("historico", historico, "pacientes", List.of(bam.getPaciente())));
        response.put("prescricaoItens", prescricaoItens);
        response.put("pedidosMedicos", pedidosMedicos);
        response.put("faturamentoItens", faturamentoItens);
        response.put("cicloMedicacaoEventos", cicloMedicacaoEventos);
        response.put("pendenciasBaixas", pendenciasBaixas);
        response.put("conformidade", conformidade);
        response.put("timeline", eventos);
        response.put("resumoOperacional", montarResumoOperacional(bam, prescricaoItens, pedidosMedicos, conformidade, faturamentoItens));
        LocalDate dataBam = toLocalDate(bam.getData());
        MarcosLog marcosLog = dataBam == null ? MarcosLog.VAZIO : carregarMarcosDoLog(dataBam, dataBam.plusDays(3), bam.getUnidade());
        response.put("indicadoresTempo", montarIndicadoresTempo(bam, eventos, listarMarcosMedicos(List.of(bam.getIdbam()), marcosLog).get(bam.getIdbam())));
        return response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> montarPepPorBam(int idbam) {
        Map<String, Object> jornada = montarPorBam(idbam);
        if (Boolean.FALSE.equals(jornada.get("encontrado"))) {
            return jornada;
        }

        Bam bam = (Bam) jornada.get("bam");
        List<Map<String, Object>> timeline =
                (List<Map<String, Object>>) jornada.getOrDefault("timeline", List.of());
        List<PrescricaoItemEstruturado> prescricaoItens =
                (List<PrescricaoItemEstruturado>) jornada.getOrDefault("prescricaoItens", List.of());
        List<PedidoMedico2> pedidosMedicos =
                (List<PedidoMedico2>) jornada.getOrDefault("pedidosMedicos", List.of());
        List<Map<String, Object>> faturamentoItens =
                (List<Map<String, Object>>) jornada.getOrDefault("faturamentoItens", List.of());
        List<PrescricaoItemCicloEvento> cicloMedicacaoEventos =
                (List<PrescricaoItemCicloEvento>) jornada.getOrDefault("cicloMedicacaoEventos", List.of());
        Map<String, Object> resumoOperacional =
                (Map<String, Object>) jornada.getOrDefault("resumoOperacional", Map.of());
        Map<String, Object> conformidade =
                (Map<String, Object>) jornada.getOrDefault("conformidade", Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idbam", idbam);
        response.put("encontrado", true);
        response.put("conceito", "PEP integrado do atendimento: paciente/prontuario + atendimento/BAM + registros assistenciais.");
        response.put("paciente", resumoPacientePep(bam));
        response.put("atendimento", resumoAtendimentoPep(bam));
        response.put("sinaisVitais", resumoSinaisVitaisPep(bam));
        response.put("mews", resumoMewsPep(bam));
        response.put("avaliacaoInicial", secao("Avaliacao inicial", List.of(resumoAvaliacaoInicialPep(bam))));
        response.put("evolucao", secao("Evolucao", filtrarEventosPep(timeline, "ATENDIMENTO", "EVOLUCAO_MEDICA", "EVOLUCAO_ENF", "ANAMNESE")));
        response.put("enfermagem", secao("Enfermagem", filtrarEventosPep(timeline, "EVOLUCAO_ENF", "ANOTACAO_ENFERMAGEM")));
        response.put("prescricao", secao("Prescricao medica", prescricaoPep(timeline, prescricaoItens)));
        response.put("exames", secao("Exames e resultados", examesPep(timeline, pedidosMedicos)));
        response.put("medicacao", medicacaoPep(prescricaoItens, cicloMedicacaoEventos, resumoOperacional));
        response.put("internacao", secao("Internacao, leito, alta e transferencia", filtrarEventosPep(timeline, "INTERNACAO", "NIR", "RESUMO_ALTA")));
        response.put("cirurgia", secao("Cirurgia e apoio assistencial", filtrarEventosPep(timeline, "CIRURGIA", "APOIO_CIRURGICO")));
        response.put("faturamento", secao("Faturamento", faturamentoItens));
        response.put("pendencias", resumoOperacional.getOrDefault("pendencias", List.of()));
        response.put("conformidade", conformidade);
        response.put("timeline", timeline);
        response.put("links", List.of(
                Map.of("label", "Jornada completa", "to", "/base2/jornada-pa?bam=" + idbam),
                Map.of("label", "Historico do paciente", "to", "/base2/historico-paciente?bam=" + idbam),
                Map.of("label", "Prontuario completo", "to", "/base2/prontuario-completo?bam=" + idbam),
                Map.of("label", "Ciclo de medicacao", "to", "/base2/ciclo-medicacao?bam=" + idbam)
        ));
        return response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> montarConformidadeAtendimento(int idbam) {
        return montarConformidadeAtendimento(idbam, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> montarConformidadeAtendimento(int idbam, String unidadeLogada) {
        Map<String, Object> jornada = montarPorBam(idbam);
        if (Boolean.FALSE.equals(jornada.get("encontrado"))) {
            return jornada;
        }

        Bam bam = (Bam) jornada.get("bam");
        if (!unidadePermitida(bam, unidadeLogada)) {
            Map<String, Object> bloqueio = new LinkedHashMap<>();
            bloqueio.put("idbam", idbam);
            bloqueio.put("encontrado", false);
            bloqueio.put("bloqueado", true);
            bloqueio.put("mensagem", "BAM nao pertence a unidade logada.");
            bloqueio.put("unidadeLogada", unidadeNormalizada(unidadeLogada));
            return bloqueio;
        }

        Map<String, Object> resumo = (Map<String, Object>) jornada.getOrDefault("resumoOperacional", Map.of());
        Map<String, Object> conformidade = (Map<String, Object>) jornada.getOrDefault("conformidade", Map.of());
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) jornada.getOrDefault("timeline", List.of());
        List<?> pedidosMedicos = (List<?>) jornada.getOrDefault("pedidosMedicos", List.of());
        List<?> prescricaoItens = (List<?>) jornada.getOrDefault("prescricaoItens", List.of());
        List<String> pendenciasOperacionais = (List<String>) resumo.getOrDefault("pendencias", List.of());
        List<Map<String, Object>> pendenciasAssinatura =
                (List<Map<String, Object>>) conformidade.getOrDefault("pendencias", List.of());
        Map<String, ConformidadePendenciaBaixa> baixas =
                conformidadePendenciaBaixaService.ultimasPorChave(idbam);
        List<Map<String, Object>> pendenciasOperacionaisAtivas = pendenciasOperacionais.stream()
                .map(item -> pendencia("Operacional", item))
                .filter(item -> !conformidadePendenciaBaixaService.estaBaixada(baixas, String.valueOf(item.get("chavePendencia"))))
                .toList();
        List<Map<String, Object>> pendenciasAssinaturaAtivas = pendenciasAssinatura.stream()
                .map(item -> {
                    Map<String, Object> pendencia = new LinkedHashMap<>(item);
                    String titulo = String.valueOf(item.getOrDefault("titulo", item.getOrDefault("tipo", "Documento clinico")));
                    pendencia.put("titulo", titulo);
                    pendencia.put("tipoPendencia", "Assinatura");
                    pendencia.put("chavePendencia", chavePendencia("Assinatura", titulo));
                    return pendencia;
                })
                .filter(item -> !conformidadePendenciaBaixaService.estaBaixada(baixas, String.valueOf(item.get("chavePendencia"))))
                .toList();
        List<Map<String, Object>> pendenciasTratadas = baixas.values().stream()
                .filter(baixa -> conformidadePendenciaBaixaService.estaBaixada(baixas, baixa.getChavePendencia()))
                .map(conformidadePendenciaBaixaService::toMap)
                .toList();

        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("assinatura", "Assinaturas", numeroResumo(resumo, "assinaturasPendentes"),
                numeroResumo(resumo, "assinaturasPendentes") == 0 ? "OK" : "PENDENTE",
                "Documento clinico pendente compromete nao repudio."));
        cards.add(card("exames", "Exames e pedidos", numeroResumo(resumo, "pedidosPendentes"),
                numeroResumo(resumo, "pedidosPendentes") == 0 ? "OK" : "PENDENTE",
                "Pedido sem conclusao operacional."));
        cards.add(card("medicacao", "Medicacao", numeroResumo(resumo, "medicacoesPendentes")
                        + numeroResumo(resumo, "medicacoesPendentesFarmacia")
                        + numeroResumo(resumo, "medicacoesPendentesRecebimento"),
                numeroResumo(resumo, "medicacoesPendentes")
                        + numeroResumo(resumo, "medicacoesPendentesFarmacia")
                        + numeroResumo(resumo, "medicacoesPendentesRecebimento") == 0 ? "OK" : "PENDENTE",
                "Fluxo de prescricao, dispensacao, recebimento e checagem."));
        cards.add(card("faturamento", "Faturamento", numeroResumo(resumo, "faturamentoReal"),
                numeroResumo(resumo, "faturamentoPendentes") == 0 ? "OK" : "PENDENTE",
                "Procedimento SUS/BPA real vinculado ao BAM."));
        cards.add(card("prontuario", "Prontuario", timeline.size(),
                timeline.isEmpty() ? "ATENCAO" : "OK",
                "Eventos clinicos e operacionais encontrados no BAM."));

        int pendencias = pendenciasOperacionaisAtivas.size() + pendenciasAssinaturaAtivas.size();
        int checks = cards.size();
        long cardsPendentes = cards.stream().filter(item -> !"OK".equals(item.get("status"))).count();
        int score = (int) Math.max(0, Math.round(((checks - cardsPendentes) * 100.0) / checks));

        Map<String, Object> pacienteResumo = new LinkedHashMap<>();
        pacienteResumo.put("idpaciente", bam.getPaciente() == null ? null : bam.getPaciente().getIdpaciente());
        pacienteResumo.put("nome", bam.getPaciente() == null ? "" : bam.getPaciente().getNome());

        Map<String, Object> atendimento = new LinkedHashMap<>();
        atendimento.put("idbam", bam.getIdbam());
        atendimento.put("data", bam.getData());
        atendimento.put("hora", bam.getHora());
        atendimento.put("unidade", bam.getUnidade());
        atendimento.put("risco", rotuloRisco(bam));
        atendimento.put("internar", bam.isInternar());
        atendimento.put("darAlta", bam.isDarAlta());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idbam", idbam);
        response.put("encontrado", true);
        response.put("status", pendencias == 0 ? "CONFORME" : "PENDENTE");
        response.put("score", score);
        response.put("paciente", pacienteResumo);
        response.put("atendimento", atendimento);
        response.put("cards", cards);
        response.put("pendencias", pendenciasOperacionaisAtivas);
        response.put("pendenciasAssinatura", pendenciasAssinaturaAtivas);
        response.put("pendenciasTratadas", pendenciasTratadas);
        // Informativo: nao entra no score/status (a fila calcula por outro caminho
        // e ficaria divergente). Resolve-se no atendimento, nao por "dar baixa".
        response.put("pendenciasSeguranca", pendenciasSegurancaPaciente(bam));
        response.put("resumoOperacional", resumo);
        response.put("conformidadeAssinatura", conformidade);
        response.put("totalEventos", timeline.size());
        response.put("totalPedidos", pedidosMedicos.size());
        response.put("totalPrescricoes", prescricaoItens.size());
        response.put("links", List.of(
                Map.of("label", "Jornada completa", "to", "/base2/jornada-pa?bam=" + idbam),
                Map.of("label", "Historico do paciente", "to", "/base2/historico-paciente?bam=" + idbam),
                Map.of("label", "Ciclo de medicacao", "to", "/base2/ciclo-medicacao?bam=" + idbam)
        ));
        return response;
    }

    /**
     * Valor critico de exame sem ciencia e medicamento de uso continuo sem decisao
     * de reconciliacao. Falha aqui nunca derruba a conformidade.
     */
    List<Map<String, Object>> pendenciasSegurancaPaciente(Bam bam) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (bam == null) return lista;
        try {
            if (resultadoCriticoService != null) {
                resultadoCriticoService.listarPorBam(bam.getIdbam()).stream()
                        .filter(c -> !c.ciente())
                        .forEach(c -> lista.add(pendenciaSeguranca(
                                "Valor critico sem ciencia: " + c.exame() + " " + c.valor()
                                        + (c.unidadeMedida() == null ? "" : " " + c.unidadeMedida()),
                                "Registrar ciencia e conduta pelo selo de valor critico no atendimento.")));
            }
            Integer idpaciente = bam.getPaciente() == null ? null : bam.getPaciente().getIdpaciente();
            if (reconciliacaoService != null && idpaciente != null && idpaciente > 0) {
                reconciliacaoService.listar(bam.getIdbam(), idpaciente).stream()
                        .filter(r -> r.get("decisao") == null)
                        .forEach(r -> lista.add(pendenciaSeguranca(
                                "Reconciliacao pendente: " + r.get("medicamento"),
                                "Decidir manter, suspender ou substituir na prescricao estruturada.")));
            }
        } catch (RuntimeException e) {
            LOG.warnf("Nao foi possivel montar pendencias de seguranca do BAM %d: %s", bam.getIdbam(), e.getMessage());
        }
        return lista;
    }

    private Map<String, Object> pendenciaSeguranca(String titulo, String orientacao) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("titulo", titulo);
        item.put("tipoPendencia", "Seguranca do paciente");
        item.put("orientacao", orientacao);
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> montarConformidadeAtendimentoFila(Bam bam, List<Map<String, Object>> eventos) {
        int idbam = bam.getIdbam();
        List<PrescricaoItemEstruturado> prescricaoItens = prescricaoItemEstruturadoService.listarPorBam(idbam, false);
        List<PedidoMedico2> pedidosMedicos = pedidoMedico2Service.filtarBam(idbam);
        Map<String, Object> conformidade = assinaturaConformidadeService.conformidadePorBam(idbam);
        List<Map<String, Object>> faturamentoItens = listarFaturamentoBam(idbam);
        Map<String, Object> resumo = montarResumoOperacional(bam, prescricaoItens, pedidosMedicos, conformidade, faturamentoItens);
        List<Map<String, Object>> timeline = eventos == null ? List.of() : eventos;

        List<String> pendenciasOperacionais = (List<String>) resumo.getOrDefault("pendencias", List.of());
        List<Map<String, Object>> pendenciasAssinatura =
                (List<Map<String, Object>>) conformidade.getOrDefault("pendencias", List.of());
        Map<String, ConformidadePendenciaBaixa> baixas =
                conformidadePendenciaBaixaService.ultimasPorChave(idbam);
        List<Map<String, Object>> pendenciasOperacionaisAtivas = pendenciasOperacionais.stream()
                .map(item -> pendencia("Operacional", item))
                .filter(item -> !conformidadePendenciaBaixaService.estaBaixada(baixas, String.valueOf(item.get("chavePendencia"))))
                .toList();
        List<Map<String, Object>> pendenciasAssinaturaAtivas = pendenciasAssinatura.stream()
                .map(item -> {
                    Map<String, Object> pendencia = new LinkedHashMap<>(item);
                    String titulo = String.valueOf(item.getOrDefault("titulo", item.getOrDefault("tipo", "Documento clinico")));
                    pendencia.put("titulo", titulo);
                    pendencia.put("tipoPendencia", "Assinatura");
                    pendencia.put("chavePendencia", chavePendencia("Assinatura", titulo));
                    return pendencia;
                })
                .filter(item -> !conformidadePendenciaBaixaService.estaBaixada(baixas, String.valueOf(item.get("chavePendencia"))))
                .toList();

        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("assinatura", "Assinaturas", numeroResumo(resumo, "assinaturasPendentes"),
                numeroResumo(resumo, "assinaturasPendentes") == 0 ? "OK" : "PENDENTE",
                "Documento clinico pendente compromete nao repudio."));
        cards.add(card("exames", "Exames e pedidos", numeroResumo(resumo, "pedidosPendentes"),
                numeroResumo(resumo, "pedidosPendentes") == 0 ? "OK" : "PENDENTE",
                "Pedido sem conclusao operacional."));
        cards.add(card("medicacao", "Medicacao", numeroResumo(resumo, "medicacoesPendentes")
                        + numeroResumo(resumo, "medicacoesPendentesFarmacia")
                        + numeroResumo(resumo, "medicacoesPendentesRecebimento"),
                numeroResumo(resumo, "medicacoesPendentes")
                        + numeroResumo(resumo, "medicacoesPendentesFarmacia")
                        + numeroResumo(resumo, "medicacoesPendentesRecebimento") == 0 ? "OK" : "PENDENTE",
                "Fluxo de prescricao, dispensacao, recebimento e checagem."));
        cards.add(card("faturamento", "Faturamento", numeroResumo(resumo, "faturamentoReal"),
                numeroResumo(resumo, "faturamentoPendentes") == 0 ? "OK" : "PENDENTE",
                "Procedimento SUS/BPA real vinculado ao BAM."));
        cards.add(card("prontuario", "Prontuario", timeline.size(),
                timeline.isEmpty() ? "ATENCAO" : "OK",
                "Eventos clinicos e operacionais encontrados no BAM."));

        int pendencias = pendenciasOperacionaisAtivas.size() + pendenciasAssinaturaAtivas.size();
        int checks = cards.size();
        long cardsPendentes = cards.stream().filter(item -> !"OK".equals(item.get("status"))).count();
        int score = (int) Math.max(0, Math.round(((checks - cardsPendentes) * 100.0) / checks));

        Map<String, Object> pacienteResumo = new LinkedHashMap<>();
        pacienteResumo.put("idpaciente", bam.getPaciente() == null ? null : bam.getPaciente().getIdpaciente());
        pacienteResumo.put("nome", bam.getPaciente() == null ? "" : bam.getPaciente().getNome());

        Map<String, Object> atendimento = new LinkedHashMap<>();
        atendimento.put("idbam", bam.getIdbam());
        atendimento.put("data", bam.getData());
        atendimento.put("hora", bam.getHora());
        atendimento.put("unidade", bam.getUnidade());
        atendimento.put("risco", rotuloRisco(bam));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idbam", idbam);
        response.put("encontrado", true);
        response.put("status", pendencias == 0 ? "CONFORME" : "PENDENTE");
        response.put("score", score);
        response.put("paciente", pacienteResumo);
        response.put("atendimento", atendimento);
        response.put("pendencias", pendenciasOperacionaisAtivas);
        response.put("pendenciasAssinatura", pendenciasAssinaturaAtivas);
        response.put("links", List.of(
                Map.of("label", "Jornada completa", "to", "/base2/jornada-pa?bam=" + idbam),
                Map.of("label", "Historico do paciente", "to", "/base2/historico-paciente?bam=" + idbam),
                Map.of("label", "Ciclo de medicacao", "to", "/base2/ciclo-medicacao?bam=" + idbam)
        ));
        return response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> montarFilaConformidade(
            LocalDate inicio,
            LocalDate fim,
            String unidade,
            String risco,
            String status,
            int limite) {
        LocalDate dataInicio = inicio == null ? LocalDate.now() : inicio;
        LocalDate dataFim = fim == null ? dataInicio : fim;
        if (dataFim.isBefore(dataInicio)) {
            LocalDate temp = dataInicio;
            dataInicio = dataFim;
            dataFim = temp;
        }

        String filtroStatus = status == null || status.isBlank() ? "TODOS" : status.trim().toUpperCase();
        String filtroRisco = risco == null || risco.isBlank() ? "TODOS" : risco.trim();
        boolean filtrarRisco = !"TODOS".equalsIgnoreCase(filtroRisco);

        List<Map<String, Object>> itens = new ArrayList<>();
        List<Bam> bams = listarBamsLimitados(dataInicio, dataFim, unidade, limite);
        Map<Integer, List<Map<String, Object>>> eventosPorBam = listarEventosEmLote(
                bams.stream().map(Bam::getIdbam).toList());
        int conformes = 0;
        int pendentes = 0;
        int atencao = 0;

        for (Bam bam : bams) {
            try {
                if (filtrarRisco && !riscoCorresponde(bam, filtroRisco)) continue;

                Map<String, Object> conformidade = montarConformidadeAtendimentoFila(
                        bam,
                        eventosPorBam.getOrDefault(bam.getIdbam(), List.of()));
                if (Boolean.FALSE.equals(conformidade.get("encontrado"))) continue;

                String statusBam = String.valueOf(conformidade.getOrDefault("status", "PENDENTE")).toUpperCase();
                int score = (int) numero(conformidade.get("score"));
                if ("CONFORME".equals(statusBam)) conformes++;
                else if (score >= 75) atencao++;
                else pendentes++;

                if (!"TODOS".equals(filtroStatus)) {
                    boolean manter = filtroStatus.equals(statusBam)
                            || ("ATENCAO".equals(filtroStatus) && score >= 75 && !"CONFORME".equals(statusBam))
                            || ("PENDENTE".equals(filtroStatus) && !"CONFORME".equals(statusBam));
                    if (!manter) continue;
                }

                Map<String, Object> atendimento = (Map<String, Object>) conformidade.getOrDefault("atendimento", Map.of());
                Map<String, Object> paciente = (Map<String, Object>) conformidade.getOrDefault("paciente", Map.of());
                List<?> pendenciasOperacionais = (List<?>) conformidade.getOrDefault("pendencias", List.of());
                List<?> pendenciasAssinatura = (List<?>) conformidade.getOrDefault("pendenciasAssinatura", List.of());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("idbam", bam.getIdbam());
                item.put("paciente", paciente.getOrDefault("nome", ""));
                item.put("idpaciente", paciente.get("idpaciente"));
                item.put("data", atendimento.get("data"));
                item.put("hora", atendimento.get("hora"));
                item.put("unidade", atendimento.get("unidade"));
                item.put("risco", atendimento.get("risco"));
                item.put("status", statusBam);
                item.put("score", score);
                item.put("pendencias", pendenciasOperacionais.size() + pendenciasAssinatura.size());
                item.put("pendenciasOperacionais", pendenciasOperacionais.size());
                item.put("pendenciasAssinatura", pendenciasAssinatura.size());
                item.put("resumoPendencias", resumoPendenciasFila(pendenciasOperacionais, pendenciasAssinatura));
                item.put("links", conformidade.getOrDefault("links", List.of()));
                itens.add(item);
            } catch (Exception e) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("idbam", bam.getIdbam());
                item.put("paciente", bam.getPaciente() == null ? "" : bam.getPaciente().getNome());
                item.put("data", bam.getData());
                item.put("hora", bam.getHora());
                item.put("unidade", bam.getUnidade());
                item.put("risco", rotuloRisco(bam));
                item.put("status", "ATENCAO");
                item.put("score", 0);
                item.put("pendencias", 1);
                item.put("resumoPendencias", List.of("Falha ao consolidar conformidade do BAM"));
                itens.add(item);
                atencao++;
            }
        }

        itens.sort(Comparator
                .comparingLong((Map<String, Object> item) -> prioridadeConformidade(item)).reversed()
                .thenComparing(item -> String.valueOf(item.getOrDefault("data", "")), Comparator.reverseOrder())
                .thenComparing(item -> String.valueOf(item.getOrDefault("hora", "")), Comparator.reverseOrder()));

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("inicio", dataInicio);
        filtros.put("fim", dataFim);
        filtros.put("unidade", unidade == null || unidade.isBlank() ? "TODAS" : unidade);
        filtros.put("risco", filtroRisco);
        filtros.put("status", filtroStatus);
        filtros.put("limite", normalizarLimite(limite));

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("total", itens.size());
        resumo.put("conformes", conformes);
        resumo.put("pendentes", pendentes);
        resumo.put("atencao", atencao);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filtros", filtros);
        response.put("resumo", resumo);
        response.put("itens", itens);
        return response;
    }

    public Map<String, Object> montarFilaPendenciasRapida(
            LocalDate inicio,
            LocalDate fim,
            String unidade,
            String risco,
            String status,
            int limite) {
        LocalDate dataInicio = inicio == null ? LocalDate.now() : inicio;
        LocalDate dataFim = fim == null ? dataInicio : fim;
        if (dataFim.isBefore(dataInicio)) {
            LocalDate temp = dataInicio;
            dataInicio = dataFim;
            dataFim = temp;
        }

        String filtroStatus = status == null || status.isBlank() ? "PENDENTE" : status.trim().toUpperCase();
        String filtroRisco = risco == null || risco.isBlank() ? "TODOS" : risco.trim();
        boolean filtrarRisco = !"TODOS".equalsIgnoreCase(filtroRisco);

        List<Bam> bams = listarBamsLimitados(dataInicio, dataFim, unidade, limite);
        List<Integer> bamIds = bams.stream().map(Bam::getIdbam).toList();
        Map<Integer, List<Map<String, Object>>> eventosPorBam = listarEventosEmLote(bamIds);
        Map<Integer, Long> faturamentoPorBam = contarFaturamentoPorBam(bamIds);

        List<Map<String, Object>> itens = new ArrayList<>();
        int conformes = 0;
        int pendentes = 0;
        int atencao = 0;

        for (Bam bam : bams) {
            if (filtrarRisco && !riscoCorresponde(bam, filtroRisco)) continue;

            List<Map<String, Object>> eventos = eventosPorBam.getOrDefault(bam.getIdbam(), List.of());
            List<String> resumoPendencias = resumoPendenciasRapido(bam, eventos, faturamentoPorBam.getOrDefault(bam.getIdbam(), 0L));
            String statusBam = resumoPendencias.isEmpty() ? "CONFORME" : "PENDENTE";
            int score = scoreRapido(resumoPendencias.size(), eventos.isEmpty());

            if ("CONFORME".equals(statusBam)) conformes++;
            else if (score >= 75) atencao++;
            else pendentes++;

            if (!"TODOS".equals(filtroStatus)) {
                boolean manter = filtroStatus.equals(statusBam)
                        || ("ATENCAO".equals(filtroStatus) && score >= 75 && !"CONFORME".equals(statusBam))
                        || ("PENDENTE".equals(filtroStatus) && !"CONFORME".equals(statusBam));
                if (!manter) continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("idbam", bam.getIdbam());
            item.put("paciente", bam.getPaciente() == null ? "" : bam.getPaciente().getNome());
            item.put("idpaciente", bam.getPaciente() == null ? null : bam.getPaciente().getIdpaciente());
            item.put("data", bam.getData());
            item.put("hora", bam.getHora());
            item.put("unidade", bam.getUnidade());
            item.put("risco", rotuloRisco(bam));
            item.put("status", statusBam);
            item.put("score", score);
            item.put("pendencias", resumoPendencias.size());
            item.put("pendenciasOperacionais", resumoPendencias.size());
            item.put("pendenciasAssinatura", 0);
            item.put("resumoPendencias", resumoPendencias);
            item.put("links", List.of(
                    Map.of("label", "Jornada completa", "to", "/base2/jornada-pa?bam=" + bam.getIdbam()),
                    Map.of("label", "Historico do paciente", "to", "/base2/historico-paciente?bam=" + bam.getIdbam()),
                    Map.of("label", "Ciclo de medicacao", "to", "/base2/ciclo-medicacao?bam=" + bam.getIdbam())
            ));
            itens.add(item);
        }

        itens.sort(Comparator
                .comparingLong((Map<String, Object> item) -> prioridadeConformidade(item)).reversed()
                .thenComparing(item -> String.valueOf(item.getOrDefault("data", "")), Comparator.reverseOrder())
                .thenComparing(item -> String.valueOf(item.getOrDefault("hora", "")), Comparator.reverseOrder()));

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("inicio", dataInicio);
        filtros.put("fim", dataFim);
        filtros.put("unidade", unidade == null || unidade.isBlank() ? "TODAS" : unidade);
        filtros.put("risco", filtroRisco);
        filtros.put("status", filtroStatus);
        filtros.put("limite", normalizarLimite(limite));
        filtros.put("modo", "RAPIDO");

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("total", itens.size());
        resumo.put("conformes", conformes);
        resumo.put("pendentes", pendentes);
        resumo.put("atencao", atencao);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filtros", filtros);
        response.put("resumo", resumo);
        response.put("itens", itens);
        return response;
    }

    public Map<String, Object> montarResumoExecutivoConformidade(
            LocalDate inicio,
            LocalDate fim,
            String unidade,
            int limitePendencias,
            int limiteBaixas) {
        return montarResumoExecutivoConformidade(inicio, fim, unidade, limitePendencias, limiteBaixas, false);
    }

    public Map<String, Object> montarResumoExecutivoConformidade(
            LocalDate inicio,
            LocalDate fim,
            String unidade,
            int limitePendencias,
            int limiteBaixas,
            boolean refresh) {
        long inicioGeral = System.nanoTime();
        Map<String, Object> desempenho = new LinkedHashMap<>();
        LocalDate dataInicio = inicio == null ? LocalDate.now() : inicio;
        LocalDate dataFim = fim == null ? dataInicio : fim;
        if (dataFim.isBefore(dataInicio)) {
            LocalDate temp = dataInicio;
            dataInicio = dataFim;
            dataFim = temp;
        }
        int limitePendenciasNormalizado = normalizarLimite(limitePendencias);
        int limiteBaixasNormalizado = normalizarLimite(limiteBaixas);
        String unidadeNormalizada = unidade == null || unidade.isBlank() ? "TODAS" : unidade.split(":")[0].trim();
        String cacheKey = String.join("|",
                "executivo",
                String.valueOf(dataInicio),
                String.valueOf(dataFim),
                unidadeNormalizada.toUpperCase(),
                String.valueOf(limitePendenciasNormalizado),
                String.valueOf(limiteBaixasNormalizado));

        if (!refresh) {
            CacheEntry cached = resumoExecutivoCache.get(cacheKey);
            if (cached != null && !cached.expirado()) {
                return respostaCache(cached, true);
            }
        }

        long inicioEtapa = System.nanoTime();
        Map<String, Object> pendencias = montarFilaPendenciasRapida(
                dataInicio,
                dataFim,
                unidadeNormalizada,
                "TODOS",
                "PENDENTE",
                limitePendenciasNormalizado);
        desempenho.put("pendenciasMs", msDesde(inicioEtapa));

        inicioEtapa = System.nanoTime();
        Map<String, Object> baixas = pendenciaBaixaService.relatorio(dataInicio, dataFim, unidadeNormalizada, limiteBaixasNormalizado);
        desempenho.put("baixasMs", msDesde(inicioEtapa));

        inicioEtapa = System.nanoTime();
        List<Map<String, Object>> fila = listaMap(pendencias.get("itens"));
        List<Map<String, Object>> registrosBaixas = listaMap(baixas.get("itens"));
        LocalDate hoje = LocalDate.now();

        long pendenciasAbertas = 0;
        long somaScore = 0;
        long somaIdade = 0;
        long foraPrazo = 0;
        Map<String, Long> porCategoria = new LinkedHashMap<>();
        Map<String, Long> porResponsavelSla = new LinkedHashMap<>();
        List<Map<String, Object>> criticos = new ArrayList<>();
        List<Map<String, Object>> pendentes = new ArrayList<>();

        for (Map<String, Object> item : fila) {
            long totalPendencias = numero(item.get("pendencias"));
            long score = numero(item.get("score"));
            long idadeMinutos = idadeAbertaMinutos(item);
            Map<String, Object> sla = slaAtendimento(item, idadeMinutos, unidade);
            item.put("sla", sla);
            pendenciasAbertas += totalPendencias;
            somaScore += score;
            somaIdade += idadeMinutos;

            if (Boolean.TRUE.equals(sla.get("foraPrazo"))) {
                foraPrazo++;
            }
            String responsavelSla = String.valueOf(sla.getOrDefault("responsavel", "Coordenacao PA"));
            if (totalPendencias > 0) {
                porResponsavelSla.put(responsavelSla, porResponsavelSla.getOrDefault(responsavelSla, 0L) + 1L);
            }

            if (totalPendencias > 0) {
                pendentes.add(item);
            }
            if (score < 75 || totalPendencias >= 3) {
                criticos.add(item);
            }

            for (Object pendencia : lista(item.get("resumoPendencias"))) {
                String categoria = categoriaDaPendencia(String.valueOf(pendencia));
                porCategoria.put(categoria, porCategoria.getOrDefault(categoria, 0L) + 1L);
            }
        }

        List<Map<String, Object>> semBaixa = criticos.stream()
                .filter(item -> registrosBaixas.stream().noneMatch(baixa -> numero(baixa.get("idbam")) == numero(item.get("idbam"))))
                .toList();
        List<Map<String, Object>> baixasHoje = registrosBaixas.stream()
                .filter(item -> hoje.equals(dataCriacao(item.get("criadoEm"))))
                .sorted(Comparator.comparing(item -> String.valueOf(((Map<String, Object>) item).getOrDefault("criadoEm", ""))).reversed())
                .toList();
        List<Map<String, Object>> foraSla = fila.stream()
                .filter(item -> {
                    Object slaValue = item.get("sla");
                    return slaValue instanceof Map<?, ?> sla && Boolean.TRUE.equals(sla.get("foraPrazo"));
                })
                .sorted(Comparator
                        .comparingLong((Map<String, Object> item) -> prioridadeSla(item)).reversed()
                        .thenComparing(Comparator.comparingLong((Map<String, Object> item) -> numero(item.get("pendencias"))).reversed()))
                .toList();

        pendentes.sort(Comparator
                .comparingLong((Map<String, Object> item) -> prioridadeSla(item)).reversed()
                .thenComparing(Comparator.comparingLong((Map<String, Object> item) -> numero(item.get("pendencias"))).reversed())
                .thenComparing(item -> numero(item.get("score"))));
        criticos.sort(Comparator
                .comparingLong((Map<String, Object> item) -> prioridadeSla(item)).reversed()
                .thenComparingLong(item -> numero(item.get("score"))));
        List<Map<String, Object>> semBaixaOrdenados = new ArrayList<>(semBaixa);
        semBaixaOrdenados.sort(Comparator
                .comparingLong((Map<String, Object> item) -> prioridadeSla(item)).reversed()
                .thenComparingLong(item -> numero(item.get("score"))));

        Map<String, Long> porResponsavel = contarPorCampo(registrosBaixas, "responsavel", "Sem responsavel");
        Map<String, Long> porStatus = contarPorCampo(registrosBaixas, "status", "Sem status");

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("scoreMedio", fila.isEmpty() ? 100 : Math.round((double) somaScore / fila.size()));
        kpis.put("pendenciasAbertas", pendenciasAbertas);
        kpis.put("casosCriticos", criticos.size());
        kpis.put("semBaixa", semBaixa.size());
        kpis.put("baixasHoje", baixasHoje.size());
        kpis.put("tempoMedioAberto", fila.isEmpty() ? 0 : Math.round((double) somaIdade / fila.size()));
        kpis.put("foraPrazo", foraPrazo);

        Map<String, Object> listas = new LinkedHashMap<>();
        listas.put("pendencias", limitar(pendentes, 8));
        listas.put("foraSla", limitar(foraSla, 8));
        listas.put("criticos", limitar(criticos, 8));
        listas.put("semBaixa", limitar(semBaixaOrdenados, 8));
        listas.put("baixasHoje", limitar(baixasHoje, 8));

        Map<String, Object> analise = new LinkedHashMap<>();
        analise.put("kpis", kpis);
        analise.put("porCategoria", ordenarContagem(porCategoria));
        analise.put("porResponsavel", limitarEntradas(ordenarContagem(porResponsavel), 5));
        analise.put("porResponsavelSla", ordenarContagem(porResponsavelSla));
        analise.put("porStatus", ordenarContagem(porStatus));
        analise.put("listas", listas);

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("inicio", dataInicio);
        filtros.put("fim", dataFim);
        filtros.put("unidade", unidadeNormalizada);
        filtros.put("limitePendencias", limitePendenciasNormalizado);
        filtros.put("limiteBaixas", limiteBaixasNormalizado);

        long totalMs = msDesde(inicioGeral);
        desempenho.put("analiseMs", msDesde(inicioEtapa));
        desempenho.put("totalMs", totalMs);
        desempenho.put("limiarLentoMs", RESUMO_EXECUTIVO_LENTO_MS);
        desempenho.put("geradoEm", LocalDateTime.now());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filtros", filtros);
        response.put("pendencias", pendencias);
        response.put("baixas", baixas);
        response.put("analise", analise);
        response.put("desempenho", desempenho);
        response.put("cache", cacheInfo(false, 0));
        resumoExecutivoCache.put(cacheKey, new CacheEntry(response, Instant.now()));
        if (totalMs > RESUMO_EXECUTIVO_LENTO_MS) {
            LOG.warnf("Resumo executivo de conformidade lento: %dms unidade=%s inicio=%s fim=%s etapas=%s",
                    totalMs,
                    unidadeNormalizada,
                    dataInicio,
                    dataFim,
                    desempenho);
        }
        return response;
    }

    public void limparCacheConformidade() {
        resumoExecutivoCache.clear();
    }

    private Map<String, Object> respostaCache(CacheEntry cached, boolean hit) {
        Map<String, Object> response = new LinkedHashMap<>(cached.response());
        response.put("cache", cacheInfo(hit, Duration.between(cached.criadoEm(), Instant.now()).toSeconds()));
        return response;
    }

    private Map<String, Object> cacheInfo(boolean hit, long idadeSegundos) {
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("hit", hit);
        cache.put("ttlSegundos", RESUMO_EXECUTIVO_CACHE_TTL.toSeconds());
        cache.put("idadeSegundos", idadeSegundos);
        cache.put("geradoEm", LocalDateTime.now());
        return cache;
    }

    private long msDesde(long inicioNano) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - inicioNano).toMillis());
    }

    private record CacheEntry(Map<String, Object> response, Instant criadoEm) {
        boolean expirado() {
            return Duration.between(criadoEm, Instant.now()).compareTo(RESUMO_EXECUTIVO_CACHE_TTL) > 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listaMap(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private List<?> lista(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private long idadeAbertaMinutos(Map<String, Object> item) {
        String dataTexto = String.valueOf(item.getOrDefault("data", ""));
        String horaTexto = String.valueOf(item.getOrDefault("hora", "00:00"));
        if (dataTexto.isBlank() || "null".equalsIgnoreCase(dataTexto)) return 0;
        try {
            LocalDate data = LocalDate.parse(dataTexto.substring(0, Math.min(10, dataTexto.length())));
            LocalTime hora = LocalTime.parse(horaTexto.substring(0, Math.min(5, horaTexto.length())));
            return Math.max(0, Duration.between(LocalDateTime.of(data, hora), LocalDateTime.now()).toMinutes());
        } catch (Exception e) {
            return 0;
        }
    }

    private LocalDate dataCriacao(Object value) {
        if (value instanceof LocalDateTime dataHora) return dataHora.toLocalDate();
        if (value instanceof LocalDate data) return data;
        String texto = String.valueOf(value);
        if (texto.length() < 10) return null;
        try {
            return LocalDate.parse(texto.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String categoriaDaPendencia(String pendencia) {
        String value = pendencia == null ? "" : pendencia.toUpperCase();
        if (value.contains("ASSINATURA") || value.contains("DOCUMENTO")) return "Assinatura";
        if (value.contains("MEDICACAO") || value.contains("CHECAGEM") || value.contains("RECEBIMENTO")) return "Medicacao";
        if (value.contains("FARMACIA") || value.contains("DISPENSACAO")) return "Farmacia";
        if (value.contains("FATURAMENTO") || value.contains("PROCEDIMENTO")) return "Faturamento";
        if (value.contains("PEDIDO") || value.contains("EXAME")) return "Pedidos";
        if (value.contains("TRIAGEM") || value.contains("RISCO")) return "Triagem";
        return "Operacional";
    }

    private Map<String, Object> slaAtendimento(Map<String, Object> item, long idadeMinutos, String unidade) {
        List<?> pendencias = lista(item.get("resumoPendencias"));
        Map<String, Object> pior = null;
        long maiorAtraso = Long.MIN_VALUE;

        if (pendencias.isEmpty() && numero(item.get("pendencias")) > 0) {
            pendencias = List.of("Pendencia operacional");
        }

        for (Object pendencia : pendencias) {
            Map<String, Object> regra = regraSlaPendencia(String.valueOf(pendencia), unidade);
            long slaMinutos = numero(regra.get("slaMinutos"));
            long atraso = idadeMinutos - slaMinutos;
            if (pior == null || atraso > maiorAtraso) {
                maiorAtraso = atraso;
                pior = new LinkedHashMap<>(regra);
                pior.put("titulo", String.valueOf(pendencia));
            }
        }

        if (pior == null) {
            pior = regraSlaPendencia("Pendencia operacional", unidade);
            pior.put("titulo", "Sem pendencia aberta");
        }

        long slaMinutos = numero(pior.get("slaMinutos"));
        long atrasoMinutos = Math.max(0, idadeMinutos - slaMinutos);
        pior.put("idadeMinutos", idadeMinutos);
        pior.put("atrasoMinutos", atrasoMinutos);
        pior.put("foraPrazo", atrasoMinutos > 0 && numero(item.get("pendencias")) > 0);
        pior.put("status", Boolean.TRUE.equals(pior.get("foraPrazo")) ? "FORA_PRAZO" : "NO_PRAZO");
        return pior;
    }

    private Map<String, Object> regraSlaPendencia(String pendencia, String unidade) {
        String categoria = categoriaDaPendencia(pendencia);
        Map<String, Object> regra = new LinkedHashMap<>(configuracaoConformidadeSlaService.regraPara(unidade, categoria));
        regra.put("categoria", categoria);
        return regra;
    }

    @SuppressWarnings("unchecked")
    private long prioridadeSla(Map<String, Object> item) {
        Object slaValue = item.get("sla");
        if (!(slaValue instanceof Map<?, ?> sla)) return 0;
        long atraso = numero(((Map<String, Object>) sla).get("atrasoMinutos"));
        return Boolean.TRUE.equals(sla.get("foraPrazo")) ? atraso + 100_000 : 0;
    }

    private Map<String, Long> contarPorCampo(List<Map<String, Object>> itens, String campo, String padrao) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> item : itens) {
            String chave = String.valueOf(item.getOrDefault(campo, "")).trim();
            if (chave.isBlank() || "null".equalsIgnoreCase(chave)) chave = padrao;
            result.put(chave, result.getOrDefault(chave, 0L) + 1L);
        }
        return result;
    }

    private List<Map<String, Object>> limitar(List<Map<String, Object>> itens, int limite) {
        return itens.stream().limit(Math.max(0, limite)).toList();
    }

    private List<Map<String, Object>> ordenarContagem(Map<String, Long> contagem) {
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", entry.getKey());
                    item.put("total", entry.getValue());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> limitarEntradas(List<Map<String, Object>> itens, int limite) {
        return itens.stream().limit(Math.max(0, limite)).toList();
    }

    private List<String> resumoPendenciasRapido(Bam bam, List<Map<String, Object>> eventos, long faturamentoReal) {
        long pedidosPendentes = eventos.stream()
                .filter(item -> "PEDIDO".equals(String.valueOf(item.get("tipo"))))
                .filter(item -> {
                    String status = String.valueOf(item.get("status")).toUpperCase();
                    return !status.contains("FEITO") && !status.contains("COLETADO");
                })
                .count();
        long prescricoesPendentes = eventos.stream()
                .filter(item -> "PRESCRICAO".equals(String.valueOf(item.get("tipo"))))
                .filter(item -> {
                    String status = String.valueOf(item.get("status")).toUpperCase();
                    return !status.contains("FEITO") && !status.contains("CANCEL");
                })
                .count();

        List<String> pendencias = new ArrayList<>();
        if (eventos.isEmpty()) pendencias.add("Atendimento sem eventos clinicos/operacionais localizados");
        if (pedidosPendentes > 0) pendencias.add(pedidosPendentes + " pedido(s) sem conclusao operacional");
        if (prescricoesPendentes > 0) pendencias.add(prescricoesPendentes + " prescricao(oes) sem conclusao operacional");
        if (bam.getTratamento() != null && faturamentoReal == 0) {
            pendencias.add("Atendimento sem procedimento real de faturamento localizado");
        }
        return pendencias;
    }

    private int scoreRapido(int pendencias, boolean semEventos) {
        int base = semEventos ? 70 : 100;
        return Math.max(0, base - (pendencias * 15));
    }

    private Map<Integer, Long> contarFaturamentoPorBam(List<Integer> bamIds) {
        if (bamIds == null || bamIds.isEmpty()) return Map.of();
        try {
            List<?> rows = entityManager.createNativeQuery("""
                    SELECT bam, count(*)
                    FROM afaturar
                    WHERE bam IN (:bamIds)
                    GROUP BY bam
                    """)
                    .setParameter("bamIds", bamIds)
                    .getResultList();
            Map<Integer, Long> result = new LinkedHashMap<>();
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                result.put((int) numero(cols[0]), numero(cols[1]));
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> resumoPendenciasFila(List<?> operacionais, List<?> assinaturas) {
        List<String> result = new ArrayList<>();
        for (Object item : operacionais) {
            if (result.size() >= 4) break;
            if (item instanceof Map<?, ?> map) {
                Object titulo = map.get("titulo");
                result.add(titulo == null ? "Pendencia operacional" : String.valueOf(titulo));
            } else if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        for (Object item : assinaturas) {
            if (result.size() >= 4) break;
            if (item instanceof Map<?, ?> map) {
                Object titulo = map.get("titulo");
                result.add(titulo == null ? "Documento clinico sem assinatura" : String.valueOf(titulo));
            } else if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private long prioridadeConformidade(Map<String, Object> item) {
        long pendencias = numero(item.get("pendencias"));
        long scoreInvertido = 100 - numero(item.get("score"));
        return pendencias * 1000 + scoreInvertido;
    }

    public Map<String, Object> montarBi(LocalDate inicio, LocalDate fim, String unidade, String risco, int limite) {
        LocalDate dataInicio = inicio == null ? LocalDate.now() : inicio;
        LocalDate dataFim = fim == null ? dataInicio : fim;
        if (dataFim.isBefore(dataInicio)) {
            LocalDate temp = dataInicio;
            dataInicio = dataFim;
            dataFim = temp;
        }

        // Todos os BAMs do periodo entram nos tempos medios; "limite" so corta a lista de
        // gargalos. Antes eram so os 50 BAMs mais recentes, e mudar a data nao mudava nada.
        List<Bam> bams = listarBamsBi(dataInicio, dataFim, unidade);
        // Finalizacoes podem cair dias depois da entrada (observacao, BAM esquecido aberto).
        MarcosLog marcosLog = carregarMarcosDoLog(dataInicio, dataFim.plusDays(3), unidade);
        Map<String, AcumuladorTempo> tempos = acumuladoresTempo();
        String filtroRisco = risco == null ? "" : risco.trim();
        boolean filtrarRisco = !filtroRisco.isBlank() && !"TODOS".equalsIgnoreCase(filtroRisco);
        long totalBamsFiltrados = 0;
        Map<String, Long> porDia = new LinkedHashMap<>();
        Map<String, Long> porRisco = new LinkedHashMap<>();
        Map<String, Long> porUnidade = new LinkedHashMap<>();
        List<Map<String, Object>> gargalos = new ArrayList<>();

        for (int lote = 0; lote < bams.size(); lote += LOTE_BI) {
            List<Bam> bamsLote = bams.subList(lote, Math.min(bams.size(), lote + LOTE_BI));
            List<Integer> idsLote = bamsLote.stream().map(Bam::getIdbam).toList();
            Map<Integer, List<Map<String, Object>>> eventosPorBam = listarEventosEmLote(idsLote);
            Map<Integer, Object[]> finalizacoes = listarMarcosMedicos(idsLote, marcosLog);
          for (Bam bam : bamsLote) {
            try {
                String riscoBam = rotuloRisco(bam);
                porRisco.merge(riscoBam, 1L, Long::sum);
                if (filtrarRisco && !riscoCorresponde(bam, filtroRisco)) continue;

                totalBamsFiltrados++;
                LocalDate dataBam = toLocalDate(bam.getData());
                if (dataBam != null) porDia.merge(String.valueOf(dataBam), 1L, Long::sum);
                porUnidade.merge(valorOuPadrao(bam.getUnidade(), "Sem unidade"), 1L, Long::sum);

                List<Map<String, Object>> eventos = eventosPorBam.getOrDefault(bam.getIdbam(), List.of());
                Object[] finalizacao = finalizacoes.get(bam.getIdbam());
                List<Map<String, Object>> indicadores = montarIndicadoresTempo(bam, eventos, finalizacao);
                for (Map<String, Object> indicador : indicadores) {
                    String key = String.valueOf(indicador.get("key"));
                    AcumuladorTempo acumulador = tempos.get(key);
                    if (acumulador != null) acumulador.adicionar(indicador.get("minutos"), indicador.get("status"));
                }

                indicadores.stream()
                        .filter(item -> "warn".equals(String.valueOf(item.get("status"))))
                        .forEach(item -> gargalos.add(gargalo(bam, item, finalizacao)));
            } catch (Exception e) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("idbam", bam.getIdbam());
                item.put("paciente", bam.getPaciente() == null ? "" : bam.getPaciente().getNome());
                item.put("data", String.valueOf(bam.getData()));
                item.put("unidade", bam.getUnidade());
                item.put("risco", rotuloRisco(bam));
                item.put("indicador", "Falha ao consolidar BAM");
                item.put("minutos", 0);
                item.put("tempo", "verificar registro");
                gargalos.add(item);
            }
          }
        }

        gargalos.sort(Comparator.comparingLong(item -> -numero(item.get("minutos"))));

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("inicio", dataInicio);
        filtros.put("fim", dataFim);
        filtros.put("unidade", unidade == null || unidade.isBlank() ? "TODAS" : unidade);
        filtros.put("risco", risco == null || risco.isBlank() ? "TODOS" : risco);
        filtros.put("limite", normalizarLimite(limite));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filtros", filtros);
        response.put("totalBams", bams.size());
        response.put("bamsTruncados", bams.size() >= MAX_BAMS_BI);
        response.put("totalBamsFiltrados", totalBamsFiltrados);
        response.put("indicadoresTempo", tempos.values().stream().map(AcumuladorTempo::toMap).toList());
        response.put("porDia", mapaSerie(porDia, "data"));
        response.put("porRisco", mapaSerie(porRisco, "risco"));
        response.put("porUnidade", mapaSerie(porUnidade, "unidade"));
        response.put("gargalos", gargalos.stream().limit(normalizarLimite(limite)).toList());
        return response;
    }
    // Entidades completas, so os "limite" BAMs mais recentes (telas de conformidade).
    private List<Bam> listarBamsLimitados(LocalDate inicio, LocalDate fim, String unidade, int limite) {
        StringBuilder jpql = new StringBuilder("select b from Bam b where b.data between :inicio and :fim");
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(b.unidade) = upper(:unidade)");
        }
        jpql.append(" order by b.data desc, b.hora desc, b.idbam desc");

        Query query = entityManager.createQuery(jpql.toString(), Bam.class)
                .setParameter("inicio", java.sql.Date.valueOf(inicio))
                .setParameter("fim", java.sql.Date.valueOf(fim))
                .setMaxResults(normalizarLimite(limite));
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)) query.setParameter("unidade", unidade);
        return query.getResultList();
    }

    private static final int MAX_BAMS_BI = 5000;
    private static final int LOTE_BI = 1000;

    // Consulta nativa so com os campos do BI: carregar a entidade Bam (6 relacoes EAGER)
    // para milhares de BAMs dispararia centenas de selects. Os Bam montados aqui sao
    // transitorios, nunca persistidos.
    private List<Bam> listarBamsBi(LocalDate inicio, LocalDate fim, String unidade) {
        boolean filtrarUnidade = unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade);
        StringBuilder sql = new StringBuilder("""
                SELECT b.idbam, b.data, b.hora, b.unidade, b.cor, b.internar, a.risco, p.nome
                FROM bam b
                LEFT JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao
                LEFT JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
                WHERE b.data BETWEEN :inicio AND :fim
                """);
        if (filtrarUnidade) sql.append(" AND upper(b.unidade) = upper(:unidade)");
        sql.append(" ORDER BY b.data DESC, b.hora DESC, b.idbam DESC");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("inicio", java.sql.Date.valueOf(inicio))
                .setParameter("fim", java.sql.Date.valueOf(fim))
                .setMaxResults(MAX_BAMS_BI);
        if (filtrarUnidade) query.setParameter("unidade", unidade);

        List<Bam> result = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] cols = (Object[]) row;
            Bam bam = new Bam();
            bam.setIdbam((int) numero(cols[0]));
            LocalDate data = toLocalDate(cols[1]);
            bam.setData(data == null ? null : java.sql.Date.valueOf(data));
            bam.setHora(cols[2] == null ? null : String.valueOf(cols[2]));
            bam.setUnidade(cols[3] == null ? null : String.valueOf(cols[3]));
            bam.setCor((int) numero(cols[4]));
            bam.setInternar(Boolean.TRUE.equals(cols[5]));
            Avaliacao avaliacao = new Avaliacao();
            avaliacao.setRisco(cols[6] == null ? null : String.valueOf(cols[6]));
            bam.setAvaliacao(avaliacao);
            Pacientes paciente = new Pacientes();
            paciente.setNome(cols[7] == null ? "" : String.valueOf(cols[7]));
            bam.setPaciente(paciente);
            result.add(bam);
        }
        return result;
    }

    private boolean ehInteiro(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean riscoCorresponde(Bam bam, String risco) {
        if (risco == null || risco.isBlank() || "TODOS".equalsIgnoreCase(risco)) return true;
        if (ehInteiro(risco)) return bam.getCor() == Integer.parseInt(risco);
        return rotuloRisco(bam).equalsIgnoreCase(risco);
    }
    private int normalizarLimite(int limite) {
        if (limite <= 0) return 50;
        return Math.min(limite, 50);
    }

    public List<String> listarRiscos() {
        try {
            return entityManager.createQuery(
                    "select distinct b.avaliacao.risco from Bam b where b.avaliacao.risco is not null and b.avaliacao.risco <> '' order by b.avaliacao.risco",
                    String.class).getResultList();
        } catch (Exception e) {
            return List.of("VERMELHO", "LARANJA", "AMARELO", "VERDE", "AZUL");
        }
    }

    private Map<String, AcumuladorTempo> acumuladoresTempo() {
        Map<String, AcumuladorTempo> result = new LinkedHashMap<>();
        result.put("porta-triagem", new AcumuladorTempo("porta-triagem", "Porta -> triagem"));
        result.put("triagem-medico", new AcumuladorTempo("triagem-medico", "Triagem -> medico"));
        result.put("medico-pedido", new AcumuladorTempo("medico-pedido", "Medico -> pedido"));
        result.put("pedido-resultado", new AcumuladorTempo("pedido-resultado", "Pedido -> resultado"));
        result.put("entrada-saida", new AcumuladorTempo("entrada-saida", "Entrada -> saida/NIR"));
        result.put("entrada-finalizacao", new AcumuladorTempo("entrada-finalizacao", "Entrada -> finalizacao medica"));
        return result;
    }

    // Por BAM: { finalizacao medica, tipo_alta, abertura pelo medico }. As colunas V215/V216
    // so existem para BAMs novos; para os antigos, os mesmos marcos saem do log de auditoria
    // (PATCH /bam/{id}/em-atendimento/true e POST /tratamento/{id}/alta/...), que tem hora.
    private Map<Integer, Object[]> listarMarcosMedicos(List<Integer> bamIds, MarcosLog marcosLog) {
        Map<Integer, Object[]> result = new LinkedHashMap<>();
        if (bamIds == null || bamIds.isEmpty()) return result;
        try {
            List<?> rows = entityManager.createNativeQuery("""
                    SELECT b.idbam, t.finalizado_em, t.tipo_alta, b.atendimento_medico_em, b.tratamento_idtratamento
                    FROM bam b
                    LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                    WHERE b.idbam IN (:bamIds)
                    """)
                    .setParameter("bamIds", bamIds)
                    .getResultList();
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                int idbam = (int) numero(cols[0]);
                int idtratamento = (int) numero(cols[4]);
                Object finalizado = cols[1];
                Object tipo = cols[2];
                Object aberto = cols[3];
                if (finalizado == null && marcosLog.finalizacaoPorTratamento().containsKey(idtratamento)) {
                    Object[] doLog = marcosLog.finalizacaoPorTratamento().get(idtratamento);
                    finalizado = doLog[0];
                    if (tipo == null || String.valueOf(tipo).isBlank()) tipo = doLog[1];
                }
                if (aberto == null) aberto = marcosLog.aberturaPorBam().get(idbam);
                result.put(idbam, new Object[] { finalizado, tipo, aberto });
            }
        } catch (Exception e) {
            LOG.warnf(e, "Falha ao carregar finalizacao medica dos BAMs no BI da PA");
        }
        return result;
    }

    private record MarcosLog(Map<Integer, LocalDateTime> aberturaPorBam, Map<Integer, Object[]> finalizacaoPorTratamento) {
        static final MarcosLog VAZIO = new MarcosLog(Map.of(), Map.of());
    }

    private static final java.util.regex.Pattern LOG_ABERTURA = java.util.regex.Pattern.compile("PATCH /bam/(\\d+)/em-atendimento/true");
    private static final java.util.regex.Pattern LOG_FINALIZACAO = java.util.regex.Pattern.compile("POST /tratamento/(\\d+)/alta/([a-z]+)");

    // Primeira abertura por BAM e primeira finalizacao por tratamento no periodo. "nirfinalizou"
    // fica de fora: e o NIR fechando (e o id da URL e o BAM, nao o tratamento).
    private MarcosLog carregarMarcosDoLog(LocalDate inicio, LocalDate fim, String unidade) {
        boolean filtrarUnidade = unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade);
        try {
            Query query = entityManager.createNativeQuery("SELECT evento, data, hora FROM logs WHERE data BETWEEN :inicio AND :fim"
                    + (filtrarUnidade ? " AND upper(unidade) = upper(:unidade)" : "")
                    + " AND (evento LIKE '%/em-atendimento/true%' OR evento LIKE '%POST /tratamento/%/alta/%')")
                    .setParameter("inicio", java.sql.Date.valueOf(inicio))
                    .setParameter("fim", java.sql.Date.valueOf(fim));
            if (filtrarUnidade) query.setParameter("unidade", unidade);

            Map<Integer, LocalDateTime> abertura = new java.util.HashMap<>();
            Map<Integer, Object[]> finalizacao = new java.util.HashMap<>();
            for (Object row : query.getResultList()) {
                Object[] cols = (Object[]) row;
                String evento = cols[0] == null ? "" : String.valueOf(cols[0]);
                LocalDateTime quando = dataHora(cols[1], cols[2]);
                if (quando == null) continue;
                java.util.regex.Matcher m = LOG_ABERTURA.matcher(evento);
                if (m.find()) {
                    abertura.merge(Integer.parseInt(m.group(1)), quando, (a, b) -> a.isBefore(b) ? a : b);
                    continue;
                }
                m = LOG_FINALIZACAO.matcher(evento);
                if (m.find() && !"nirfinalizou".equals(m.group(2))) {
                    Object[] novo = new Object[] { quando, m.group(2) };
                    finalizacao.merge(Integer.parseInt(m.group(1)), novo,
                            (a, b) -> ((LocalDateTime) a[0]).isBefore((LocalDateTime) b[0]) ? a : b);
                }
            }
            return new MarcosLog(abertura, finalizacao);
        } catch (Exception e) {
            LOG.warnf(e, "Falha ao ler marcos medicos do log no BI da PA");
            return MarcosLog.VAZIO;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toLocalDateTime();
        if (value instanceof java.time.Instant instant) return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dataHora(String.valueOf(value), null);
    }

    private Map<String, Object> gargalo(Bam bam, Map<String, Object> indicador, Object[] finalizacao) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idbam", bam.getIdbam());
        item.put("paciente", bam.getPaciente() == null ? "" : bam.getPaciente().getNome());
        item.put("data", toLocalDate(bam.getData()));
        item.put("unidade", bam.getUnidade());
        item.put("risco", rotuloRisco(bam));
        item.put("internado", bam.isInternar());
        item.put("indicador", indicador.get("label"));
        item.put("minutos", indicador.get("minutos"));
        item.put("tempo", indicador.get("value"));
        LocalDateTime finalizadoEm = finalizacao == null ? null : toLocalDateTime(finalizacao[0]);
        Long permanencia = minutosEntre(dataHora(bam.getData(), bam.getHora()), finalizadoEm);
        item.put("permanenciaMinutos", permanencia);
        item.put("permanencia", formatarDuracao(permanencia));
        item.put("finalizadoEm", finalizadoEm == null ? null : String.valueOf(finalizadoEm));
        item.put("destino", finalizacao == null || finalizacao[1] == null ? "" : String.valueOf(finalizacao[1]));
        return item;
    }

    private List<Map<String, Object>> mapaSerie(Map<String, Long> mapa, String chave) {
        return mapa.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put(chave, entry.getKey());
                    item.put("total", entry.getValue());
                    return item;
                })
                .toList();
    }

    private String rotuloRisco(Bam bam) {
        if (bam.getAvaliacao() != null && bam.getAvaliacao().getRisco() != null && !bam.getAvaliacao().getRisco().isBlank()) {
            return bam.getAvaliacao().getRisco();
        }
        return String.valueOf(bam.getCor());
    }

    private String valorOuPadrao(String value, String padrao) {
        return value == null || value.isBlank() ? padrao : value;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private static class AcumuladorTempo {
        private final String key;
        private final String label;
        private long totalMinutos;
        private long amostras;
        private long ok;
        private long atencao;
        private long critico;

        private AcumuladorTempo(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private void adicionar(Object minutosValue, Object statusValue) {
            if (!(minutosValue instanceof Number number)) return;
            long minutos = number.longValue();
            totalMinutos += minutos;
            amostras++;

            String status = String.valueOf(statusValue);
            if ("ok".equals(status)) ok++;
            else if ("warn".equals(status)) critico++;
            else atencao++;
        }

        private Map<String, Object> toMap() {
            long media = amostras == 0 ? 0 : Math.round((double) totalMinutos / amostras);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", label);
            item.put("mediaMinutos", amostras == 0 ? null : media);
            item.put("media", amostras == 0 ? "-" : formatarDuracaoStatic(media));
            item.put("amostras", amostras);
            item.put("ok", ok);
            item.put("atencao", atencao);
            item.put("critico", critico);
            item.put("status", critico > 0 ? "warn" : atencao > 0 ? "neutral" : amostras > 0 ? "ok" : "neutral");
            return item;
        }

        private static String formatarDuracaoStatic(long minutos) {
            if (minutos < 60) return minutos + " min";
            long horas = minutos / 60;
            long resto = minutos % 60;
            return resto > 0 ? horas + "h " + resto + "min" : horas + "h";
        }
    }

    private List<Map<String, Object>> montarIndicadoresTempo(Bam bam, List<Map<String, Object>> eventos, Object[] marcos) {
        LocalDateTime entrada = dataHora(bam.getData(), bam.getHora());
        List<Map<String, Object>> ordenados = eventos.stream()
                .sorted(Comparator.comparing(this::dataHoraEvento, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // Marcos escolhidos pelo TIPO do evento, nunca por texto livre: o relato da
        // enfermagem com "medicacao" ou o rotulo "Pedido:" do detalhe faziam qualquer
        // evento virar "medico"/"pedido". Eventos sem hora (caem em 00:00) tambem nao
        // servem para medir minutos entre etapas.
        Map<String, Object> triagem = primeiroEvento(ordenados, item -> tipoEvento(item, "TRIAGEM"));
        LocalDateTime inicioMedico = dataHoraEvento(triagem) != null ? dataHoraEvento(triagem) : entrada;
        // A recepcao cria um registro em "atendimento" vazio (sem relato/medico) no
        // momento da entrada; so conta como medico quando o medico preencheu algo.
        Map<String, Object> consulta = primeiroEventoApos(ordenados, inicioMedico, item -> tipoEvento(item, "EVOLUCAO_MEDICA")
                || (tipoEvento(item, "ATENDIMENTO") && atendimentoPreenchido(item)));
        Map<String, Object> pedido = primeiroEventoApos(ordenados, inicioMedico, item -> tipoEvento(item, "PEDIDO", "PRESCRICAO"));
        // A hora em que o medico abriu o paciente (V216) e o marco preferido. Para BAMs
        // anteriores a ela, sem registro proprio da consulta, o primeiro pedido/prescricao
        // e o primeiro ato medico com hora — serve para triagem->medico, nao para medico->pedido.
        LocalDateTime finalizadoEm = marcos == null ? null : toLocalDateTime(marcos[0]);
        LocalDateTime abertoPeloMedico = marcos == null || marcos.length < 3 ? null : toLocalDateTime(marcos[2]);
        if (abertoPeloMedico != null && inicioMedico != null && abertoPeloMedico.isBefore(inicioMedico)) abertoPeloMedico = null;
        LocalDateTime fimTriagemMedico = abertoPeloMedico != null ? abertoPeloMedico
                : dataHoraEvento(consulta != null ? consulta : pedido);
        LocalDateTime inicioPedido = abertoPeloMedico != null ? abertoPeloMedico : dataHoraEvento(consulta);
        Map<String, Object> pedidoAposConsulta = inicioPedido == null ? null
                : primeiroEventoApos(ordenados, inicioPedido, item -> tipoEvento(item, "PEDIDO", "PRESCRICAO"));
        Map<String, Object> resultado = primeiroEventoApos(ordenados, dataHoraEvento(pedido), item -> tipoEvento(item, "RESULTADO_LAB"));
        // NIR/internacao/resumo de alta nao tem hora (so data); com hora, valem. Sem eles, a
        // saida do fluxo da PA e a finalizacao do medico (alta, internacao ou observacao).
        Map<String, Object> saidaEvento = primeiroEventoApos(ordenados, entrada, item -> tipoEvento(item, "NIR", "INTERNACAO", "RESUMO_ALTA"));
        LocalDateTime saida = saidaEvento != null ? dataHoraEvento(saidaEvento) : finalizadoEm;

        return List.of(
                indicadorTempo("porta-triagem", "Porta -> triagem", entrada, dataHoraEvento(triagem), "Entrada ate classificacao", 10, 30),
                indicadorTempo("triagem-medico", "Triagem -> medico", inicioMedico, fimTriagemMedico, "Classificacao ate atendimento", 30, 90),
                indicadorTempo("medico-pedido", "Medico -> pedido", inicioPedido, dataHoraEvento(pedidoAposConsulta), "Atendimento ate solicitacao", 20, 60),
                indicadorTempo("pedido-resultado", "Pedido -> resultado", dataHoraEvento(pedido), dataHoraEvento(resultado), "Solicitacao ate resultado", 60, 180),
                indicadorTempo("entrada-saida", "Entrada -> saida/NIR", entrada, saida, "Permanencia ate destino", 240, 720),
                indicadorTempo("entrada-finalizacao", "Entrada -> finalizacao medica", entrada, finalizadoEm, "Permanencia ate o medico finalizar o BAM", 240, 720));
    }

    private Map<String, Object> indicadorTempo(String key, String label, LocalDateTime inicio, LocalDateTime fim, String meta, long limiteBom, long limiteAtencao) {
        Long minutos = minutosEntre(inicio, fim);
        String status = minutos == null ? "neutral" : minutos <= limiteBom ? "ok" : minutos <= limiteAtencao ? "neutral" : "warn";

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("value", formatarDuracao(minutos));
        item.put("minutos", minutos);
        item.put("meta", minutos == null ? "Aguardando dado" : meta);
        item.put("status", status);
        return item;
    }

    private Long minutosEntre(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) return null;
        return Duration.between(inicio, fim).toMinutes();
    }

    private String formatarDuracao(Long minutos) {
        if (minutos == null) return "-";
        if (minutos < 60) return minutos + " min";
        long horas = minutos / 60;
        long resto = minutos % 60;
        return resto > 0 ? horas + "h " + resto + "min" : horas + "h";
    }

    private Map<String, Object> primeiroEvento(List<Map<String, Object>> eventos, java.util.function.Predicate<Map<String, Object>> predicate) {
        return eventos.stream()
                .filter(item -> dataHoraEvento(item) != null)
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> primeiroEventoApos(List<Map<String, Object>> eventos, LocalDateTime inicio,
                                                   java.util.function.Predicate<Map<String, Object>> predicate) {
        if (inicio == null) return null;
        return eventos.stream()
                .filter(this::temHora)
                .filter(item -> !dataHoraEvento(item).isBefore(inicio))
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    private boolean tipoEvento(Map<String, Object> item, String... tipos) {
        String tipo = String.valueOf(item.get("tipo"));
        for (String t : tipos) {
            if (t.equals(tipo)) return true;
        }
        return false;
    }

    private boolean temHora(Map<String, Object> item) {
        Object hora = item.get("hora");
        String texto = hora == null ? "" : String.valueOf(hora).trim();
        return dataHoraEvento(item) != null && !texto.isEmpty() && !"null".equalsIgnoreCase(texto);
    }

    private boolean atendimentoPreenchido(Map<String, Object> item) {
        Object profissional = item.get("profissional");
        boolean temMedico = profissional != null && !String.valueOf(profissional).isBlank();
        Object detalhe = item.get("detalhe");
        return temMedico || (detalhe != null && !String.valueOf(detalhe).isBlank());
    }

    private LocalDateTime dataHoraEvento(Map<String, Object> item) {
        if (item == null) return null;
        return dataHora(item.get("data"), item.get("hora"));
    }

    private LocalDateTime dataHora(Object data, Object hora) {
        if (data == null || String.valueOf(data).isBlank()) return null;
        String dataTexto = String.valueOf(data).trim();
        String horaTexto = hora == null ? "" : String.valueOf(hora).trim();

        try {
            if (dataTexto.contains("T")) return LocalDateTime.parse(dataTexto.substring(0, Math.min(19, dataTexto.length())));
            LocalDate localDate = parseData(dataTexto);
            if (localDate == null) return null;
            LocalTime localTime = parseHora(horaTexto);
            return LocalDateTime.of(localDate, localTime);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDate parseData(String data) {
        String valor = data.length() > 10 ? data.substring(0, 10) : data;
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"))) {
            try {
                return LocalDate.parse(valor, formatter);
            } catch (DateTimeParseException ignored) {
                // Tenta o proximo formato.
            }
        }
        return null;
    }

    private LocalTime parseHora(String hora) {
        if (hora == null || hora.isBlank() || "null".equalsIgnoreCase(hora)) return LocalTime.MIDNIGHT;
        String valor = hora.length() >= 8 ? hora.substring(0, 8) : hora;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_TIME, DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("H:mm:ss"))) {
            try {
                return LocalTime.parse(valor, formatter);
            } catch (DateTimeParseException ignored) {
                // Tenta o proximo formato.
            }
        }
        return LocalTime.MIDNIGHT;
    }

    private Map<String, Object> montarResumoOperacional(
            Bam bam,
            List<PrescricaoItemEstruturado> prescricaoItens,
            List<PedidoMedico2> pedidosMedicos,
            Map<String, Object> conformidade,
            List<Map<String, Object>> faturamentoItens) {
        int idpaciente = bam.getPaciente() == null ? 0 : bam.getPaciente().getIdpaciente();
        String unidade = bam.getUnidade() == null ? "" : bam.getUnidade();

        Map<String, Long> prontuario = new LinkedHashMap<>();
        if (idpaciente > 0) {
            prontuarioClinicoItemService.listar(idpaciente, unidade).stream()
                    .filter(item -> "ATIVO".equalsIgnoreCase(String.valueOf(item.getStatus())))
                    .forEach(item -> prontuario.merge(String.valueOf(item.getTipo()), 1L, Long::sum));
        }

        long prescricoesAtivas = prescricaoItens.stream()
                .filter(item -> {
                    String status = String.valueOf(item.getStatus()).toUpperCase();
                    return !status.contains("CANCEL") && !status.contains("SUSP") && !status.contains("INAT");
                })
                .count();
        long prescricoesSuspensas = prescricaoItens.stream()
                .filter(item -> String.valueOf(item.getStatus()).toUpperCase().contains("SUSP"))
                .count();
        Map<String, Long> medicacao = resumoMedicacaoPorBam(bam.getIdbam());
        long dosesAprazadas = medicacao.getOrDefault("aprazadas", 0L);
        long dosesChecadas = medicacao.getOrDefault("checadas", 0L);
        long itensDispensados = medicacao.getOrDefault("dispensadas", 0L);
        long itensRecebidos = medicacao.getOrDefault("recebidas", 0L);
        long pendentesFarmacia = Math.max(prescricoesAtivas - itensDispensados, 0);
        long pendentesRecebimento = Math.max(itensDispensados - itensRecebidos, 0);
        long dosesPendentes = Math.max(dosesAprazadas - dosesChecadas, 0);

        long pedidosPendentes = pedidosMedicos.stream()
                .filter(item -> !ativo(item.isEfetuado()) && !ativo(item.isConfirmado()))
                .count();
        long faturamentoReal = faturamentoItens == null ? 0L : faturamentoItens.size();
        long faturamentoPendentes = bam.getTratamento() != null && faturamentoReal == 0 ? 1L : 0L;

        int assinaturasPendentes = extrairTotalPendencias(conformidade);
        List<String> pendencias = new ArrayList<>();
        if (prontuario.getOrDefault("ALERGIA", 0L) > 0) pendencias.add("Paciente possui alergia ativa");
        if (pendentesFarmacia > 0) pendencias.add(pendentesFarmacia + " item(ns) pendente(s) de dispensacao pela farmacia");
        if (pendentesRecebimento > 0) pendencias.add(pendentesRecebimento + " item(ns) dispensado(s) pendente(s) de recebimento pela enfermagem");
        if (pedidosPendentes > 0) pendencias.add(pedidosPendentes + " pedido(s) sem conclusao operacional");
        if (dosesPendentes > 0) pendencias.add(dosesPendentes + " dose(s) de medicacao pendente(s) de checagem");
        if (faturamentoPendentes > 0) pendencias.add("Atendimento sem procedimento real de faturamento localizado");
        if (assinaturasPendentes > 0) pendencias.add(assinaturasPendentes + " documento(s) pendente(s) de assinatura");

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("alergias", prontuario.getOrDefault("ALERGIA", 0L));
        resumo.put("problemasAtivos", prontuario.getOrDefault("PROBLEMA_ATIVO", 0L));
        resumo.put("antecedentes", prontuario.getOrDefault("ANTECEDENTE", 0L));
        resumo.put("diagnosticos", prontuario.getOrDefault("DIAGNOSTICO", 0L));
        resumo.put("medicamentosContinuos", prontuario.getOrDefault("MEDICAMENTO_CONTINUO", 0L));
        resumo.put("prescricoesAtivas", prescricoesAtivas);
        resumo.put("medicacoesPrescritas", prescricaoItens.size());
        resumo.put("medicacoesDispensadas", itensDispensados);
        resumo.put("medicacoesRecebidas", itensRecebidos);
        resumo.put("medicacoesAprazadas", dosesAprazadas);
        resumo.put("medicacoesChecadas", dosesChecadas);
        resumo.put("medicacoesPendentes", dosesPendentes);
        resumo.put("medicacoesPendentesFarmacia", pendentesFarmacia);
        resumo.put("medicacoesPendentesRecebimento", pendentesRecebimento);
        resumo.put("medicacoesSuspensas", prescricoesSuspensas);
        resumo.put("pedidosPendentes", pedidosPendentes);
        resumo.put("faturamentoReal", faturamentoReal);
        resumo.put("faturamentoPendentes", faturamentoPendentes);
        resumo.put("assinaturasPendentes", assinaturasPendentes);
        resumo.put("pendencias", pendencias);
        resumo.put("status", pendencias.isEmpty() ? "OK" : "PENDENTE");
        return resumo;
    }

    private Map<String, Object> resumoPacientePep(Bam bam) {
        Map<String, Object> paciente = new LinkedHashMap<>();
        if (bam == null || bam.getPaciente() == null) return paciente;
        paciente.put("idpaciente", bam.getPaciente().getIdpaciente());
        paciente.put("nome", bam.getPaciente().getNome());
        paciente.put("prontuario", bam.getPaciente().getProntuario());
        paciente.put("idade", bam.getPaciente().getIdade());
        paciente.put("sexo", bam.getPaciente().getSexo());
        paciente.put("pacienteEspecial", bam.getPaciente().getPacienteespecial());
        return paciente;
    }

    private Map<String, Object> resumoAtendimentoPep(Bam bam) {
        Map<String, Object> atendimento = new LinkedHashMap<>();
        if (bam == null) return atendimento;
        atendimento.put("idbam", bam.getIdbam());
        atendimento.put("data", bam.getData());
        atendimento.put("hora", bam.getHora());
        atendimento.put("unidade", bam.getUnidade());
        atendimento.put("setor", bam.getRegistro() == null ? null : bam.getRegistro().getSetor());
        atendimento.put("especialidade", bam.getRegistro() == null ? null : bam.getRegistro().getEspecialidade());
        atendimento.put("tipoChegada", bam.getRegistro() == null ? null : bam.getRegistro().getTipochegada());
        atendimento.put("risco", bam.getAvaliacao() == null ? null : bam.getAvaliacao().getRisco());
        atendimento.put("internar", bam.isInternar());
        atendimento.put("darAlta", bam.isDarAlta());
        atendimento.put("emAtendimento", bam.isEmatendimento());
        atendimento.put("transferido", bam.isTransferido());
        atendimento.put("tipoAlta", bam.getTratamento() == null ? null : bam.getTratamento().getTipo_alta());
        atendimento.put("destino", bam.getTratamento() == null ? null : bam.getTratamento().getDestino());
        return atendimento;
    }

    private Map<String, Object> resumoSinaisVitaisPep(Bam bam) {
        Map<String, Object> sinais = new LinkedHashMap<>();
        if (bam == null || bam.getAvaliacao() == null) return sinais;
        sinais.put("paSistolica", bam.getAvaliacao().getPasist());
        sinais.put("paDiastolica", bam.getAvaliacao().getPadiast());
        sinais.put("frequenciaCardiaca", bam.getAvaliacao().getFc());
        sinais.put("frequenciaRespiratoria", bam.getAvaliacao().getFr());
        sinais.put("temperatura", bam.getAvaliacao().getTax());
        sinais.put("saturacaoOxigenio", bam.getAvaliacao().getSpo());
        sinais.put("peso", bam.getAvaliacao().getPeso());
        sinais.put("glasgow", bam.getAvaliacao().getGlasgow());
        sinais.put("nivelDor", bam.getAvaliacao().getNiveldor());
        sinais.put("data", bam.getAvaliacao().getData());
        sinais.put("hora", bam.getAvaliacao().getHora());
        return sinais;
    }

    private Map<String, Object> resumoMewsPep(Bam bam) {
        Map<String, Object> sinais = resumoSinaisVitaisPep(bam);
        int score = 0;
        score += pontoMews(numero(sinais.get("frequenciaCardiaca")), 40, 50, 100, 110, 129);
        score += pontoMews(numero(sinais.get("frequenciaRespiratoria")), 8, 14, 20, 29, 30);
        score += pontoMews(numero(sinais.get("paSistolica")), 70, 80, 100, 199, 200);
        score += pontoTemperaturaMews(valorDouble(sinais.get("temperatura")));

        Map<String, Object> mews = new LinkedHashMap<>();
        mews.put("scoreEstimado", score);
        mews.put("status", score >= 5 ? "ALERTA" : score >= 3 ? "ATENCAO" : "OK");
        mews.put("observacao", "Escore estimado para apoio visual; protocolo institucional deve prevalecer.");
        return mews;
    }

    private int pontoMews(long valor, long criticoBaixo, long baixo, long normalAlto, long alertaAlto, long criticoAlto) {
        if (valor <= 0) return 0;
        if (valor <= criticoBaixo || valor >= criticoAlto) return 3;
        if (valor <= baixo || valor >= alertaAlto) return 2;
        if (valor > normalAlto) return 1;
        return 0;
    }

    private int pontoTemperaturaMews(double valor) {
        if (valor <= 0) return 0;
        if (valor < 35.0 || valor >= 38.5) return 2;
        return 0;
    }

    private double valorDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(value).replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Map<String, Object> resumoAvaliacaoInicialPep(Bam bam) {
        Map<String, Object> avaliacao = new LinkedHashMap<>();
        if (bam == null) return avaliacao;
        if (bam.getRegistro() != null) {
            avaliacao.put("queixaPrincipal", bam.getRegistro().getQueixaprincipal());
            avaliacao.put("obsEntrada", bam.getRegistro().getObsentrada());
            avaliacao.put("tipoOcorrencia", bam.getRegistro().getTipoocorrencia());
        }
        if (bam.getAvaliacao() != null) {
            avaliacao.put("anamnese", bam.getAvaliacao().getAnamnese());
            avaliacao.put("hpp", bam.getAvaliacao().getHpp());
            avaliacao.put("comentario", bam.getAvaliacao().getComentario());
            avaliacao.put("risco", bam.getAvaliacao().getRisco());
        }
        if (bam.getTratamento() != null) {
            avaliacao.put("diagnosticoProvisorio", bam.getTratamento().getDiagnostico_provisorio());
            avaliacao.put("prescricaoInicial", bam.getTratamento().getPrescricao_inicial());
            avaliacao.put("examesSolicitados", bam.getTratamento().getExames_solicitados());
        }
        return avaliacao;
    }

    private Map<String, Object> secao(String titulo, List<?> itens) {
        List<?> lista = itens == null ? List.of() : itens;
        Map<String, Object> secao = new LinkedHashMap<>();
        secao.put("titulo", titulo);
        secao.put("total", lista.size());
        secao.put("itens", lista);
        return secao;
    }

    private List<Map<String, Object>> filtrarEventosPep(List<Map<String, Object>> eventos, String... tipos) {
        if (eventos == null || eventos.isEmpty()) return List.of();
        List<String> permitidos = List.of(tipos);
        return eventos.stream()
                .filter(item -> permitidos.contains(String.valueOf(item.get("tipo")).toUpperCase()))
                .toList();
    }

    private List<Map<String, Object>> prescricaoPep(List<Map<String, Object>> timeline, List<PrescricaoItemEstruturado> itens) {
        List<Map<String, Object>> prescricao = new ArrayList<>(filtrarEventosPep(timeline, "PRESCRICAO"));
        if (itens != null) {
            itens.forEach(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getIditem());
                row.put("tipo", "PRESCRICAO_ESTRUTURADA");
                row.put("titulo", item.getMedicamento());
                row.put("tipoItem", item.getTipoItem());
                row.put("dose", item.getDose());
                row.put("via", item.getVia());
                row.put("frequencia", item.getFrequencia());
                row.put("duracao", item.getDuracao());
                row.put("status", item.getStatus());
                row.put("criadoEm", item.getCriadoEm());
                row.put("profissional", item.getPrescritoPor());
                prescricao.add(row);
            });
        }
        return prescricao;
    }

    private List<Map<String, Object>> examesPep(List<Map<String, Object>> timeline, List<PedidoMedico2> pedidos) {
        List<Map<String, Object>> exames = new ArrayList<>(filtrarEventosPep(timeline, "PEDIDO", "RESULTADO_LAB", "RESULTADO_RX", "RESULTADO_TOMO"));
        if (pedidos != null) {
            pedidos.forEach(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getIdpedidomedico());
                row.put("tipo", "PEDIDO_MEDICO");
                row.put("titulo", item.getPedido());
                row.put("data", item.getDt());
                row.put("hora", item.getHr());
                row.put("profissional", item.getMedico());
                row.put("local", item.getLocal());
                row.put("efetuado", item.isEfetuado());
                row.put("confirmado", item.isConfirmado());
                exames.add(row);
            });
        }
        return exames;
    }

    private Map<String, Object> medicacaoPep(
            List<PrescricaoItemEstruturado> itens,
            List<PrescricaoItemCicloEvento> eventos,
            Map<String, Object> resumoOperacional) {
        Map<String, Object> medicacao = new LinkedHashMap<>();
        medicacao.put("resumo", resumoOperacional == null ? Map.of() : resumoOperacional);
        medicacao.put("itens", itens == null ? List.of() : prescricaoPep(List.of(), itens));
        medicacao.put("eventosCiclo", eventos == null ? List.of() : eventos);
        return medicacao;
    }

    private Map<String, Long> resumoMedicacaoPorBam(int idbam) {
        Map<String, Long> eventos = resumoEventosMedicacaoPorBam(idbam);
        String sql = """
                select count(*) as aprazadas,
                       sum(case when checado = true then 1 else 0 end) as checadas
                from prescricao_aprazamento_checagem
                where idbam = :idbam
                """;
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(sql)
                    .setParameter("idbam", idbam)
                    .getSingleResult();
            Map<String, Long> result = new LinkedHashMap<>();
            result.put("aprazadas", Math.max(numero(row[0]), eventos.getOrDefault("aprazadas", 0L)));
            result.put("checadas", Math.max(numero(row[1]), eventos.getOrDefault("checadas", 0L)));
            result.put("dispensadas", eventos.getOrDefault("dispensadas", 0L));
            result.put("recebidas", eventos.getOrDefault("recebidas", 0L));
            return result;
        } catch (Exception e) {
            Map<String, Long> result = new LinkedHashMap<>();
            result.put("aprazadas", eventos.getOrDefault("aprazadas", 0L));
            result.put("checadas", eventos.getOrDefault("checadas", 0L));
            eventos.forEach(result::put);
            return result;
        }
    }

    private Map<String, Long> resumoEventosMedicacaoPorBam(int idbam) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("dispensadas", 0L);
        result.put("recebidas", 0L);
        result.put("aprazadas", 0L);
        result.put("checadas", 0L);
        try {
            List<PrescricaoItemCicloEvento> eventos = prescricaoItemCicloEventoService.listarPorBam(idbam);
            result.put("dispensadas", contarItensComEvento(eventos, "DISPENSACAO"));
            result.put("recebidas", contarItensComEvento(eventos, "RECEBIMENTO_ENFERMAGEM"));
            result.put("aprazadas", contarItensComEvento(eventos, "APRAZAMENTO"));
            result.put("checadas", contarItensComEvento(eventos, "CHECAGEM"));
        } catch (Exception e) {
            return result;
        }
        return result;
    }

    private long contarItensComEvento(List<PrescricaoItemCicloEvento> eventos, String tipoEvento) {
        return eventos.stream()
                .filter(evento -> tipoEvento.equalsIgnoreCase(String.valueOf(evento.getTipoEvento())))
                .map(PrescricaoItemCicloEvento::getIditem)
                .distinct()
                .count();
    }

    private long numero(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long numeroResumo(Map<String, Object> resumo, String key) {
        if (resumo == null) return 0L;
        return numero(resumo.get(key));
    }

    private Map<String, Object> card(String key, String label, long valor, String status, String detalhe) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("key", key);
        card.put("label", label);
        card.put("valor", valor);
        card.put("status", status);
        card.put("detalhe", detalhe);
        return card;
    }

    private Map<String, Object> pendencia(String tipo, String titulo) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tipoPendencia", tipo);
        item.put("tipo", tipo);
        item.put("titulo", titulo == null || titulo.isBlank() ? "Pendencia operacional" : titulo);
        item.put("chavePendencia", chavePendencia(tipo, titulo));
        return item;
    }

    private String chavePendencia(String tipo, String titulo) {
        String chave = (String.valueOf(tipo) + ":" + String.valueOf(titulo))
                .trim()
                .replaceAll("\\s+", " ");
        return chave.length() > 220 ? chave.substring(0, 220) : chave;
    }

    private boolean unidadePermitida(Bam bam, String unidadeLogada) {
        String unidade = unidadeNormalizada(unidadeLogada);
        if (unidade.isBlank()) return true;
        return unidade.equals(unidadeNormalizada(bam == null ? null : bam.getUnidade()));
    }

    private String unidadeNormalizada(String unidade) {
        if (unidade == null) return "";
        return unidade
                .split(":")[0]
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private int extrairTotalPendencias(Map<String, Object> conformidade) {
        if (conformidade == null) return 0;
        Object pendencias = conformidade.get("pendencias");
        if (pendencias instanceof List<?> lista) return lista.size();
        Object total = conformidade.get("totalPendencias");
        if (total instanceof Number number) return number.intValue();
        return 0;
    }

    private Map<String, Object> resumoBam(Bam bam) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idbam", bam.getIdbam());
        result.put("data", bam.getData());
        result.put("hora", bam.getHora());
        result.put("unidade", bam.getUnidade());
        result.put("cor", bam.getCor());
        result.put("internar", bam.isInternar());
        result.put("ematendimento", bam.isEmatendimento());
        result.put("transferido", bam.isTransferido());

        if (bam.getRegistro() != null) {
            result.put("setor", bam.getRegistro().getSetor());
            result.put("especialidade", bam.getRegistro().getEspecialidade());
            result.put("tipochegada", bam.getRegistro().getTipochegada());
            result.put("tipoocorrencia", bam.getRegistro().getTipoocorrencia());
            result.put("queixaPrincipal", bam.getRegistro().getQueixaprincipal());
            result.put("obsentrada", bam.getRegistro().getObsentrada());
        }

        if (bam.getAvaliacao() != null) {
            result.put("risco", bam.getAvaliacao().getRisco());
            result.put("anamnese", bam.getAvaliacao().getAnamnese());
            result.put("comentario", bam.getAvaliacao().getComentario());
            result.put("hpp", bam.getAvaliacao().getHpp());
            result.put("pasist", bam.getAvaliacao().getPasist());
            result.put("padiast", bam.getAvaliacao().getPadiast());
            result.put("fc", bam.getAvaliacao().getFc());
            result.put("fr", bam.getAvaliacao().getFr());
            result.put("spo", bam.getAvaliacao().getSpo());
            result.put("tax", bam.getAvaliacao().getTax());
            result.put("peso", bam.getAvaliacao().getPeso());
            result.put("glasgow", bam.getAvaliacao().getGlasgow());
            result.put("niveldor", bam.getAvaliacao().getNiveldor());
        }

        if (bam.getTratamento() != null) {
            result.put("diagnosticoProvisorio", bam.getTratamento().getDiagnostico_provisorio());
            result.put("examesSolicitados", bam.getTratamento().getExames_solicitados());
            result.put("prescricaoInicial", bam.getTratamento().getPrescricao_inicial());
            result.put("destino", bam.getTratamento().getDestino());
            result.put("encaminhamento", bam.getTratamento().getEncaminhamento());
            result.put("tipoAlta", bam.getTratamento().getTipo_alta());
            result.put("tratado", bam.getTratamento().getTratado());
            result.put("obsSaida", bam.getTratamento().getObs_saida());
            result.put("medicoecrm", bam.getTratamento().getMedicoecrm());
        }

        return result;
    }

    private List<Map<String, Object>> listarFaturamentoBam(int bam) {
        String sql = """
                SELECT idafaturar, procedimento, descricao, local, localsolicitante,
                       matriculaservidor, nomeservidor, bam, data, dtatendimento,
                       cid10, cbo, obs, realizado, unidade, origeminfo, pa_tpreg
                FROM afaturar
                WHERE bam = :bam
                ORDER BY COALESCE(dtatendimento, data) DESC, idafaturar DESC
                """;

        try {
            List<Map<String, Object>> rows = rows(
                    entityManager.createNativeQuery(sql).setParameter("bam", bam),
                    "idafaturar", "procedimento", "descricao", "local", "localsolicitante",
                    "matriculaservidor", "nomeservidor", "bam", "data", "dtatendimento",
                    "cid10", "cbo", "obs", "realizado", "unidade", "origeminfo", "pa_tpreg");

            return rows.stream().map(row -> {
                Map<String, Object> item = new LinkedHashMap<>();
                Object descricao = row.get("descricao");
                item.put("id", row.get("idafaturar"));
                item.put("idafaturar", row.get("idafaturar"));
                item.put("tipo", "FATURAMENTO");
                item.put("titulo", descricao == null || String.valueOf(descricao).isBlank()
                        ? "Procedimento SUS"
                        : String.valueOf(descricao));
                item.put("procedimento", row.get("procedimento"));
                item.put("descricao", row.get("descricao"));
                item.put("bam", row.get("bam"));
                item.put("data", row.get("data"));
                item.put("dtatendimento", row.get("dtatendimento"));
                item.put("profissional", row.get("nomeservidor"));
                item.put("nomeservidor", row.get("nomeservidor"));
                item.put("matriculaservidor", row.get("matriculaservidor"));
                item.put("local", row.get("local"));
                item.put("localsolicitante", row.get("localsolicitante"));
                item.put("cid10", row.get("cid10"));
                item.put("cbo", row.get("cbo"));
                item.put("origeminfo", row.get("origeminfo"));
                item.put("pa_tpreg", row.get("pa_tpreg"));
                item.put("detalhe", detalheFaturamento(row));
                item.put("status", ativo(row.get("realizado")) ? "Faturado" : "A faturar");
                item.put("faturamentoReal", true);
                return item;
            }).toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String detalheFaturamento(Map<String, Object> row) {
        List<String> detalhes = new ArrayList<>();
        if (row.get("obs") != null && !String.valueOf(row.get("obs")).isBlank()) detalhes.add("Obs: " + row.get("obs"));
        if (row.get("cid10") != null && !String.valueOf(row.get("cid10")).isBlank()) detalhes.add("CID: " + row.get("cid10"));
        if (row.get("cbo") != null && !String.valueOf(row.get("cbo")).isBlank()) detalhes.add("CBO: " + row.get("cbo"));
        if (row.get("origeminfo") != null && !String.valueOf(row.get("origeminfo")).isBlank()) detalhes.add("Origem: " + row.get("origeminfo"));
        return String.join(" | ", detalhes);
    }

    private Map<String, Object> resumoEventos(
            List<Map<String, Object>> eventos,
            List<PrescricaoItemEstruturado> prescricaoItens,
            List<PedidoMedico2> pedidosMedicos) {
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("totalBams", 1);
        resumo.put("totalAtendimentos", contarTipos(eventos, List.of("ATENDIMENTO", "EVOLUCAO_MEDICA", "EVOLUCAO_ENF")));
        resumo.put("totalPedidos", pedidosMedicos.size());
        resumo.put("totalPrescricoes", prescricaoItens.size() + contarTipos(eventos, List.of("PRESCRICAO")));
        resumo.put("totalInternacoes", contarTipos(eventos, List.of("INTERNACAO", "NIR", "RESUMO_ALTA")));
        return resumo;
    }

    private List<Map<String, Object>> listarEventos(int bam) {
        List<Map<String, Object>> eventos = new ArrayList<>();
        eventos.addAll(listarTriagem(bam));
        eventos.addAll(listarAtendimentos(bam));
        eventos.addAll(listarPrescricoes(bam));
        eventos.addAll(listarPedidos(bam));
        eventos.addAll(listarResultadosLaboratorio(bam));
        eventos.addAll(listarEvolucoesMedicas(bam));
        eventos.addAll(listarEvolucoesEnfermagem(bam));
        eventos.addAll(listarAnamneseInternacao(bam));
        eventos.addAll(listarInternacoes(bam));
        eventos.addAll(listarNir(bam));
        eventos.addAll(listarResumoAlta(bam));
        eventos.sort(Comparator.comparing(this::chaveOrdenacao).reversed());
        return eventos;
    }

    private List<Map<String, Object>> listarApoioCirurgico(int idpaciente) {
        String sql = "SELECT idevidencia, data_registro, NULL AS hora, responsavel, unidade, modulo, tipo, status, referencia, descricao "
                + "FROM evidencia_assistencial WHERE idpaciente = :idpaciente AND origemmodulo = 'CENTRO_CIRURGICO' "
                + "ORDER BY data_registro DESC";
        try {
            List<Map<String, Object>> registros = rows(entityManager.createNativeQuery(sql).setParameter("idpaciente", idpaciente),
                    "idevidencia", "data_registro", "hora", "responsavel", "unidade", "modulo", "tipo", "status", "referencia", "descricao");
            List<Map<String, Object>> eventos = new ArrayList<>();
            for (Map<String, Object> registro : registros) {
                Map<String, Object> evento = new LinkedHashMap<>();
                evento.put("tipo", "APOIO_CIRURGICO");
                evento.put("titulo", "Apoio cirurgico - " + registro.get("modulo"));
                evento.put("id", registro.get("idevidencia"));
                evento.put("bam", null);
                evento.put("data", registro.get("data_registro"));
                evento.put("hora", registro.get("hora"));
                evento.put("profissional", registro.get("responsavel"));
                evento.put("local", registro.get("unidade"));
                evento.put("detalhe", "Demanda: " + registro.get("tipo") + " | " + registro.get("referencia"));
                evento.put("conteudo", "<p>" + registro.get("descricao") + "</p>");
                evento.put("status", registro.get("status"));
                eventos.add(evento);
            }
            return eventos;
        } catch (Exception e) {
            LOG.warn("Falha ao carregar apoio cirurgico na Jornada PA", e);
            return new ArrayList<>();
        }
    }

    private Map<Integer, List<Map<String, Object>>> listarEventosEmLote(List<Integer> bamIds) {
        if (bamIds == null || bamIds.isEmpty()) return Map.of();
        Map<Integer, List<Map<String, Object>>> agrupado = new LinkedHashMap<>();
        for (int id : bamIds) agrupado.put(id, new ArrayList<>());

        agrupar("SELECT a.idavaliacao, a.data, a.hora, NULL AS profissional, a.unidade AS local, a.risco, a.niveldor, a.glasgow, b.idbam AS bam "
                        + "FROM bam b JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao WHERE b.idbam IN (:bamIds)",
                bamIds, agrupado, "TRIAGEM", "Classificacao de risco",
                "idavaliacao", "data", "hora", "profissional", "local", "risco", "niveldor", "glasgow");
        agrupar("SELECT idatendimento, data, hora, nomedomedico, NULL AS local, relato, receita, bam FROM atendimento WHERE bam IN (:bamIds) ORDER BY data DESC, hora DESC",
                bamIds, agrupado, "ATENDIMENTO", "Atendimento medico",
                "idatendimento", "data", "hora", "nomedomedico", "local", "relato", "receita");
        agrupar("SELECT idprescricao, data, hora, medicoq, localsolicitante, prescricao, medicacao, feito, inativo, bam FROM prescricao WHERE bam IN (:bamIds) ORDER BY data DESC, hora DESC",
                bamIds, agrupado, "PRESCRICAO", "Prescricao/medicamentos",
                "idprescricao", "data", "hora", "medicoq", "localsolicitante", "prescricao", "medicacao", "feito", "inativo");
        agrupar("SELECT idpedidomedico, dt, hr, medico, local, pedido, obs, efetuado, coletar, confirmado, internado, emergencia, liberoucoleta, bam FROM pedidomedico2 WHERE bam IN (:bamIds) ORDER BY dt DESC, hr DESC",
                bamIds, agrupado, "PEDIDO", "Pedido de exame/medicacao",
                "idpedidomedico", "dt", "hr", "medico", "local", "pedido", "obs", "efetuado", "coletar", "confirmado", "internado", "emergencia", "liberoucoleta");
        agrupar("SELECT idpedidoresultado, dt, hr, medico, local, descricao, grupopedidos, arquivo, bam FROM pedidoresultadolaboratorio WHERE bam IN (:bamIds) ORDER BY dt DESC, hr DESC",
                bamIds, agrupado, "RESULTADO_LAB", "Resultado de laboratorio",
                "idpedidoresultado", "dt", "hr", "medico", "local", "descricao", "grupopedidos", "arquivo");
        agrupar("SELECT idevolucao, data, horaev, medico, local, relato, pedido, pedidodomed, inativo, nbam AS bam FROM evolucaopaciente WHERE nbam IN (:bamIds) ORDER BY data DESC, horaev DESC",
                bamIds, agrupado, "EVOLUCAO_MEDICA", "Evolucao medica",
                "idevolucao", "data", "horaev", "medico", "local", "relato", "pedido", "pedidodomed", "inativo");
        agrupar("SELECT idevolucao, data, hora, enfermeiro, local, relato, pedido, pedidodomed, nbam AS bam FROM evolucaopacienteenf WHERE nbam IN (:bamIds) ORDER BY data DESC, hora DESC",
                bamIds, agrupado, "EVOLUCAO_ENF", "Evolucao de enfermagem",
                "idevolucao", "data", "hora", "enfermeiro", "local", "relato", "pedido", "pedidodomed");
        agrupar("SELECT idanamneseinternacao, data, NULL AS hora, medico, NULL AS local, frente, verso, crm, nbam AS bam FROM anamneseinternacao WHERE nbam IN (:bamIds) ORDER BY idanamneseinternacao DESC",
                bamIds, agrupado, "ANAMNESE_INTERNACAO", "Anamnese de internacao",
                "idanamneseinternacao", "data", "hora", "medico", "local", "frente", "verso", "crm");
        List<String> bamIdsStr = bamIds.stream().map(String::valueOf).toList();
        agrupar("SELECT idninternado, datainternacao, NULL AS hora, medicoqueinternou, local, obs, cid, datasaida, motivodaalta, medicodeualta, bam FROM internacao WHERE bam IN (:bamIds) ORDER BY datainternacao DESC",
                bamIdsStr, agrupado, "INTERNACAO", "Internacao",
                "idninternado", "datainternacao", "hora", "medicoqueinternou", "local", "obs", "cid", "datasaida", "motivodaalta", "medicodeualta");
        agrupar("SELECT n.idninternado, n.diainternacao, NULL AS hora, n.quemalocou, COALESCE(s.setor, n.tipo) AS local, n.diagnostico, n.leitoocupado, n.alta, n.motivosaida, n.pendencia, n.nbam AS bam FROM novointernado n LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso WHERE n.nbam IN (:bamIds) ORDER BY n.diainternacao DESC",
                bamIds, agrupado, "NIR", "NIR/leito",
                "idninternado", "diainternacao", "hora", "quemalocou", "local", "diagnostico", "leitoocupado", "alta", "motivosaida", "pendencia");
        agrupar("SELECT idresumoalta, dataalta, horaobito, nome, NULL AS local, relato, diagfinal, cid2, bam FROM resumodealta WHERE bam IN (:bamIds) ORDER BY idresumoalta DESC",
                bamIdsStr, agrupado, "RESUMO_ALTA", "Resumo de alta",
                "idresumoalta", "dataalta", "horaobito", "nome", "local", "relato", "diagfinal", "cid2");

        agrupado.values().forEach(lista -> lista.sort(Comparator.comparing(this::chaveOrdenacao).reversed()));
        return agrupado;
    }

    @SuppressWarnings("unchecked")
    private void agrupar(String sql, Object bamIdsParam, Map<Integer, List<Map<String, Object>>> destino,
                         String tipo, String titulo, String... fields) {
        try {
            Query query = entityManager.createNativeQuery(sql).setParameter("bamIds", bamIdsParam);
            List<Object[]> records = query.getResultList();
            for (Object[] values : records) {
                int bamId = (int) numero(values[values.length - 1]);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < fields.length; i++) {
                    row.put(fields[i], i < values.length - 1 ? normalizarValor(values[i]) : null);
                }
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("tipo", tipo);
                event.put("titulo", titulo);
                event.put("id", row.get(fields[0]));
                event.put("bam", bamId);
                event.put("data", row.get(fields.length > 1 ? fields[1] : fields[0]));
                event.put("hora", row.get(fields.length > 2 ? fields[2] : fields[0]));
                event.put("profissional", row.get(fields.length > 3 ? fields[3] : fields[0]));
                event.put("local", fields.length > 4 ? row.get(fields[4]) : null);
                event.put("detalhe", montarDetalhe(row, fields, 5));
                event.put("conteudo", montarConteudo(row, fields, 5));
                event.put("status", statusOperacional(tipo, row));
                destino.get(bamId).add(event);
            }
        } catch (Exception e) {
            // Nao interrompe a timeline, mas registra para nao virar "sem dados" silencioso.
            LOG.warnf(e, "Falha ao carregar eventos do tipo %s na jornada da PA", tipo);
        }
    }

    private List<Map<String, Object>> listarTriagem(int bam) {
        String sql = "SELECT a.idavaliacao, a.data, a.hora, NULL AS profissional, a.unidade AS local, a.risco, a.niveldor, a.glasgow "
                + "FROM bam b JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao WHERE b.idbam = :bam";
        return eventos(sql, bam, "TRIAGEM", "Classificacao de risco", "idavaliacao", "data", "hora", "profissional", "local", "risco", "niveldor", "glasgow");
    }

    private List<Map<String, Object>> listarAtendimentos(int bam) {
        String sql = "SELECT idatendimento, data, hora, nomedomedico, NULL AS local, relato, receita FROM atendimento WHERE bam = :bam ORDER BY data DESC, hora DESC";
        return eventos(sql, bam, "ATENDIMENTO", "Atendimento medico", "idatendimento", "data", "hora", "nomedomedico", "local", "relato", "receita");
    }

    private List<Map<String, Object>> listarPrescricoes(int bam) {
        String sql = "SELECT idprescricao, data, hora, medicoq, localsolicitante, prescricao, medicacao, feito, inativo FROM prescricao WHERE bam = :bam ORDER BY data DESC, hora DESC";
        return eventos(sql, bam, "PRESCRICAO", "Prescricao/medicamentos", "idprescricao", "data", "hora", "medicoq", "localsolicitante", "prescricao", "medicacao", "feito", "inativo");
    }

    private List<Map<String, Object>> listarPedidos(int bam) {
        String sql = "SELECT idpedidomedico, dt, hr, medico, local, pedido, obs, efetuado, coletar, confirmado, internado, emergencia, liberoucoleta FROM pedidomedico2 WHERE bam = :bam ORDER BY dt DESC, hr DESC";
        return eventos(sql, bam, "PEDIDO", "Pedido de exame/medicacao", "idpedidomedico", "dt", "hr", "medico", "local", "pedido", "obs", "efetuado", "coletar", "confirmado", "internado", "emergencia", "liberoucoleta");
    }

    private List<Map<String, Object>> listarResultadosLaboratorio(int bam) {
        String sql = "SELECT idpedidoresultado, dt, hr, medico, local, descricao, grupopedidos, arquivo FROM pedidoresultadolaboratorio WHERE bam = :bam ORDER BY dt DESC, hr DESC";
        return eventos(sql, bam, "RESULTADO_LAB", "Resultado de laboratorio", "idpedidoresultado", "dt", "hr", "medico", "local", "descricao", "grupopedidos", "arquivo");
    }

    private List<Map<String, Object>> listarEvolucoesMedicas(int bam) {
        String sql = "SELECT idevolucao, data, horaev, medico, local, relato, pedido, pedidodomed, inativo FROM evolucaopaciente WHERE nbam = :bam ORDER BY data DESC, horaev DESC";
        return eventos(sql, bam, "EVOLUCAO_MEDICA", "Evolucao medica", "idevolucao", "data", "horaev", "medico", "local", "relato", "pedido", "pedidodomed", "inativo");
    }

    private List<Map<String, Object>> listarEvolucoesEnfermagem(int bam) {
        String sql = "SELECT idevolucao, data, hora, enfermeiro, local, relato, pedido, pedidodomed FROM evolucaopacienteenf WHERE nbam = :bam ORDER BY data DESC, hora DESC";
        return eventos(sql, bam, "EVOLUCAO_ENF", "Evolucao de enfermagem", "idevolucao", "data", "hora", "enfermeiro", "local", "relato", "pedido", "pedidodomed");
    }

    private List<Map<String, Object>> listarAnamneseInternacao(int bam) {
        String sql = "SELECT idanamneseinternacao, data, NULL AS hora, medico, NULL AS local, frente, verso, crm FROM anamneseinternacao WHERE nbam = :bam ORDER BY idanamneseinternacao DESC";
        return eventos(sql, bam, "ANAMNESE_INTERNACAO", "Anamnese de internacao", "idanamneseinternacao", "data", "hora", "medico", "local", "frente", "verso", "crm");
    }

    private List<Map<String, Object>> listarInternacoes(int bam) {
        String sql = "SELECT idninternado, datainternacao, NULL AS hora, medicoqueinternou, local, obs, cid, datasaida, motivodaalta, medicodeualta FROM internacao WHERE bam = CAST(:bam AS text) ORDER BY datainternacao DESC";
        return eventos(sql, bam, "INTERNACAO", "Internacao", "idninternado", "datainternacao", "hora", "medicoqueinternou", "local", "obs", "cid", "datasaida", "motivodaalta", "medicodeualta");
    }

    private List<Map<String, Object>> listarNir(int bam) {
        String sql = """
                SELECT n.idninternado, n.diainternacao, NULL AS hora, n.quemalocou,
                       COALESCE(s.setor, n.tipo) AS local, n.diagnostico, n.leitoocupado, n.alta, n.motivosaida, n.pendencia
                FROM novointernado n
                LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso
                WHERE n.nbam = :bam
                ORDER BY n.diainternacao DESC
                """;
        return eventos(sql, bam, "NIR", "NIR/leito", "idninternado", "diainternacao", "hora", "quemalocou", "local", "diagnostico", "leitoocupado", "alta", "motivosaida", "pendencia");
    }

    private List<Map<String, Object>> listarResumoAlta(int bam) {
        String sql = "SELECT idresumoalta, dataalta, horaobito, nome, NULL AS local, relato, diagfinal, cid2 FROM resumodealta WHERE bam = CAST(:bam AS text) ORDER BY idresumoalta DESC";
        return eventos(sql, bam, "RESUMO_ALTA", "Resumo de alta", "idresumoalta", "dataalta", "horaobito", "nome", "local", "relato", "diagfinal", "cid2");
    }

    private List<Map<String, Object>> eventos(String sql, int bam, String tipo, String titulo, String... fields) {
        try {
            List<Map<String, Object>> rows = rows(entityManager.createNativeQuery(sql).setParameter("bam", bam), fields);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("tipo", tipo);
                event.put("titulo", titulo);
                event.put("id", row.get(fields[0]));
                event.put("bam", bam);
                event.put("data", row.get(fields.length > 1 ? fields[1] : fields[0]));
                event.put("hora", row.get(fields.length > 2 ? fields[2] : fields[0]));
                event.put("profissional", row.get(fields.length > 3 ? fields[3] : fields[0]));
                event.put("local", fields.length > 4 ? row.get(fields[4]) : null);
                event.put("detalhe", montarDetalhe(row, fields, 5));
                event.put("conteudo", montarConteudo(row, fields, 5));
                event.put("status", statusOperacional(tipo, row));
                result.add(event);
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String montarDetalhe(Map<String, Object> row, String[] fields, int start) {
        List<String> detalhes = new ArrayList<>();
        for (int i = start; i < fields.length; i++) {
            Object value = row.get(fields[i]);
            if (value != null && !String.valueOf(value).isBlank()) {
                detalhes.add(label(fields[i]) + ": " + value);
            }
        }
        return String.join(" | ", detalhes);
    }

    private String montarConteudo(Map<String, Object> row, String[] fields, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < fields.length; i++) {
            Object value = row.get(fields[i]);
            if (value != null && !String.valueOf(value).isBlank()) {
                builder.append("<p><strong>").append(label(fields[i])).append(":</strong> ").append(value).append("</p>");
            }
        }
        return builder.length() == 0 ? "<p>Sem detalhe registrado.</p>" : builder.toString();
    }

    private String statusOperacional(String tipo, Map<String, Object> row) {
        if (ativo(row.get("inativo"))) return "Cancelado";
        if ("RESULTADO_LAB".equals(tipo) || row.get("arquivo") != null) return "Feito";
        if (ativo(row.get("efetuado")) || ativo(row.get("feito")) || ativo(row.get("alta"))) return "Feito";
        if (ativo(row.get("confirmado")) || row.get("liberoucoleta") != null) return "Coletado";
        if (ativo(row.get("coletar"))) return "Aguardando coleta";
        if (List.of("TRIAGEM", "ATENDIMENTO", "EVOLUCAO_MEDICA", "EVOLUCAO_ENF", "ANAMNESE_INTERNACAO", "INTERNACAO", "NIR", "RESUMO_ALTA").contains(tipo)) {
            return "Feito";
        }
        return "Solicitado";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Query query, String... keys) {
        List<Object[]> records = query.getResultList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : records) {
            Object[] values = keys.length == 1 ? new Object[] { item } : (Object[]) item;
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                row.put(keys[i], i < values.length ? normalizarValor(values[i]) : null);
            }
            result.add(row);
        }
        return result;
    }

    private Object normalizarValor(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof Boolean) return value;
        return String.valueOf(value);
    }

    private long contarTipos(List<Map<String, Object>> eventos, List<String> tipos) {
        return eventos.stream().filter(item -> tipos.contains(String.valueOf(item.get("tipo")))).count();
    }

    private boolean ativo(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        String normalizado = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(normalizado) || "1".equals(normalizado) || "S".equalsIgnoreCase(normalizado) || "SIM".equalsIgnoreCase(normalizado);
    }

    private String chaveOrdenacao(Map<String, Object> item) {
        return String.valueOf(item.getOrDefault("data", "")) + " " + String.valueOf(item.getOrDefault("hora", ""));
    }

    private String label(String field) {
        return switch (field) {
            case "relato" -> "Relato";
            case "receita" -> "Receita";
            case "prescricao" -> "Prescricao";
            case "medicacao" -> "Medicacao";
            case "pedido" -> "Pedido";
            case "obs" -> "Observacao";
            case "efetuado" -> "Efetuado";
            case "coletar" -> "Coletar";
            case "confirmado" -> "Confirmado";
            case "internado" -> "Internado";
            case "emergencia" -> "Emergencia";
            case "liberoucoleta" -> "Liberou coleta";
            case "grupopedidos" -> "Grupo de pedidos";
            case "arquivo" -> "Arquivo";
            case "cid", "cid2" -> "CID";
            case "datasaida" -> "Data de saida";
            case "motivodaalta" -> "Motivo da alta";
            case "medicodeualta" -> "Medico da alta";
            case "leitoocupado" -> "Leito";
            case "pendencia" -> "Pendencia";
            case "diagfinal" -> "Diagnostico final";
            case "risco" -> "Risco";
            case "niveldor" -> "Nivel de dor";
            case "glasgow" -> "Glasgow";
            default -> field;
        };
    }
}
