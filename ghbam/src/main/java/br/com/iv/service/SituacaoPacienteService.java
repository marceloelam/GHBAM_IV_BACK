package br.com.iv.service;

import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Bam;
import br.com.iv.entity.NovoInternado;
import br.com.iv.entity.PedidoMedico2;
import br.com.iv.entity.Tratamento;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Onde esta o paciente agora" na unidade, a partir do ultimo BAM: uma resposta unica
 * para a busca do cabecalho, em vez de varias consultas e deducoes na tela.
 * So le dados; nao devolve texto clinico (anamnese, diagnostico, nomes de exame).
 */
@ApplicationScoped
public class SituacaoPacienteService {

    /** BAM aberto ha mais que isso, sem internacao, provavelmente saiu sem registro de alta. */
    private static final long DIAS_BAM_ABERTO_SUSPEITO = 2;

    @Inject
    BamService bamService;

    @Transactional
    public Map<String, Object> situacao(int idpaciente, String unidade) {
        Map<String, Object> r = new LinkedHashMap<>();
        Bam bam = bamService.filtrarPorIdDoPcienteUltimoBam(idpaciente, unidade);
        if (bam == null) {
            r.put("status", "SEM_REGISTRO");
            r.put("naUnidade", false);
            r.put("titulo", "Sem atendimento registrado nesta unidade");
            r.put("etapas", List.of());
            r.put("pendencias", List.of());
            return r;
        }

        Avaliacao a = bam.getAvaliacao();
        Tratamento t = bam.getTratamento();
        NovoInternado ni = NovoInternado.<NovoInternado>find("nbam = ?1 order by idninternado desc", bam.getIdbam()).firstResult();

        List<Map<String, Object>> etapas = new ArrayList<>();
        etapa(etapas, "Chegada (BAM nº " + bam.getIdbam() + ")", data(bam.getData()), bam.getHora());
        boolean classificado = a != null && (texto(a.getRisco()) != null || a.getCor() > 0);
        if (classificado) {
            etapa(etapas, "Classificação de risco" + (texto(a.getRisco()) == null ? "" : ": " + a.getRisco()), texto(a.getData()), texto(a.getHora()));
        }
        String leito = ni == null ? null : juntar(ni.getSetor() == null ? null : ni.getSetor().getSetor(),
                ni.getLocal() == null ? null : ni.getLocal().getLocal(), texto(ni.getLeitoocupado()));
        if (ni != null) {
            etapa(etapas, "Internação" + (leito == null ? "" : " - " + leito), ni.getDiainternacao() == null ? null : ni.getDiainternacao().toString(), null);
            if (ni.isAlta()) {
                etapa(etapas, "Saída da internação" + (texto(ni.getMotivosaida()) == null ? "" : ": " + ni.getMotivosaida()),
                        ni.getDatadaacao() == null ? null : ni.getDatadaacao().toString(), null);
            }
        }

        String status = classificar(ni != null, ni != null && ni.isAlta(), t != null && t.getTratado() == 1,
                t == null ? null : t.getTipo_alta(), bam.isEmatendimento(), a != null && a.getEsperando() == 1, classificado);
        boolean naUnidade = !"SAIU".equals(status);
        String titulo = switch (status) {
            case "INTERNADO" -> "Internado" + (leito == null ? "" : " - " + leito);
            case "OBSERVACAO" -> "Em observação";
            case "AGUARDANDO_LEITO" -> "Internação indicada - aguardando leito (NIR)";
            case "EM_ATENDIMENTO" -> "Em atendimento médico";
            case "EM_ESPERA" -> "Em espera (aguardando reavaliação ou resultado)";
            case "AGUARDANDO_MEDICO" -> "Classificado - aguardando atendimento médico";
            case "AGUARDANDO_CLASSIFICACAO" -> "Aguardando classificação de risco";
            default -> null;
        };
        if ("SAIU".equals(status)) {
            String destino = ni != null && ni.isAlta() ? texto(ni.getMotivosaida()) : null;
            if (destino == null && t != null) destino = texto(t.getTipo_alta());
            if (destino == null && t != null) destino = texto(t.getDestino());
            titulo = "Atendimento encerrado" + (destino == null ? "" : " - " + destino);
            if (ni == null) {
                etapa(etapas, "Atendimento encerrado" + (destino == null ? "" : ": " + destino), texto(bam.getDataDaAlta()), null);
            }
        }

        // Exames pedidos e ainda nao realizados, agrupados pelo setor que executa (sem o nome do exame).
        List<Map<String, Object>> pendencias = new ArrayList<>();
        if (naUnidade) {
            Map<String, Integer> porSetor = new TreeMap<>();
            for (PedidoMedico2 p : PedidoMedico2.<PedidoMedico2>find("bam = ?1 and efetuado = false", bam.getIdbam()).list()) {
                porSetor.merge(setorExame(p.getReal()), 1, Integer::sum);
            }
            porSetor.forEach((setor, qtd) -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("tipo", "EXAME");
                p.put("texto", qtd + " exame(s) aguardando " + setor);
                pendencias.add(p);
            });
        }

        String alerta = null;
        if (naUnidade && ni == null && bam.getData() != null) {
            long dias = ChronoUnit.DAYS.between(bam.getData().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now());
            if (dias >= DIAS_BAM_ABERTO_SUSPEITO) {
                alerta = "BAM aberto há " + dias + " dias sem internação: o paciente pode ter saído sem registro de alta.";
            }
        }

        r.put("status", status);
        r.put("naUnidade", naUnidade);
        r.put("titulo", titulo);
        r.put("idbam", bam.getIdbam());
        r.put("risco", a == null ? null : texto(a.getRisco()));
        r.put("alerta", alerta);
        r.put("etapas", etapas);
        r.put("pendencias", pendencias);
        return r;
    }

    /**
     * Etapa do BAM na unidade. Regra unica da busca do cabecalho e do quadro de fluxo
     * (FluxoPacientesService): mudar aqui muda os dois.
     */
    static String classificar(boolean temInternacao, boolean internacaoComAlta, boolean tratado, String tipoAlta,
            boolean emAtendimento, boolean esperando, boolean classificado) {
        String alta = tipoAlta == null ? "" : tipoAlta.trim().toLowerCase();
        if (temInternacao && !internacaoComAlta) return "INTERNADO";
        if (tratado || internacaoComAlta) return "SAIU";
        if (alta.startsWith("obs")) return "OBSERVACAO";
        if (alta.startsWith("intern")) return "AGUARDANDO_LEITO";
        if (emAtendimento) return "EM_ATENDIMENTO";
        if (esperando) return "EM_ESPERA";
        if (classificado) return "AGUARDANDO_MEDICO";
        return "AGUARDANDO_CLASSIFICACAO";
    }

    private static void etapa(List<Map<String, Object>> etapas, String descricao, String data, String hora) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("descricao", descricao);
        e.put("data", data);
        e.put("hora", hora);
        etapas.add(e);
    }

    private static String setorExame(String real) {
        String k = real == null ? "" : real.trim().toUpperCase();
        return switch (k) {
            case "RAIO X", "RAIOX", "RAIO_X" -> "no Raio X";
            case "LABORATORIO" -> "no Laboratório";
            case "TOMOGRAFIA", "TOMO" -> "na Tomografia";
            case "ULTRASSOM", "ULTRASSONOGRAFIA", "USG" -> "no Ultrassom";
            case "MEDICACAO" -> "no SPA (medicação)";
            case "MEDICACAO2" -> "no SPA da maternidade";
            case "" -> "(setor não informado)";
            default -> "em " + real.trim();
        };
    }

    private static String data(java.util.Date d) {
        return d == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String juntar(String... partes) {
        List<String> ok = new ArrayList<>();
        for (String p : partes) if (p != null && !p.isBlank()) ok.add(p.trim());
        return ok.isEmpty() ? null : String.join(" / ", ok);
    }
}
