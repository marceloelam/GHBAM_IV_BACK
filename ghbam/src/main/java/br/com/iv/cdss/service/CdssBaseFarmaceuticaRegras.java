package br.com.iv.cdss.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.iv.cdss.dto.CdssAlertDto;
import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.AlergiaCruzada;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Duplicidade;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Interacao;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Leva para a prescricao medica as regras da base de conhecimento farmaceutico
 * (a mesma que a farmacia usa na dispensacao). Antes o CDSS da prescricao so
 * conhecia as tabelas fixas do {@link CdssRulesEngine}; agora o que o
 * farmaceutico cadastra no JSON passa a alertar tambem no momento de prescrever.
 *
 * So acrescenta alertas que o motor fixo ainda nao emitiu, para o medico nao
 * ver o mesmo risco duas vezes.
 */
@ApplicationScoped
public class CdssBaseFarmaceuticaRegras {
    private static final Pattern DOSE_MASSA = Pattern.compile("(\\d+(?:[,.]\\d+)?)\\s*(mcg|ug|mg|g)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERVALO_FRACIONADO = Pattern.compile("(\\d+)\\s*/\\s*\\1\\s*(?:h|hora)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERVALO_A_CADA = Pattern.compile("(?:a\\s*cada|q)\\s*(\\d+(?:[,.]\\d+)?)\\s*(?:h|hora)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VEZES_AO_DIA = Pattern.compile("(\\d+)\\s*(?:x|vezes)\\s*(?:ao|por|/)?\\s*dia",
            Pattern.CASE_INSENSITIVE);
    private static final int IDADE_IDOSO = 65;

    @Inject
    BaseConhecimentoFarmaceutico base;

    public List<CdssAlertDto> avaliar(CdssAvaliacaoPrescricaoRequest request, List<CdssAlertDto> existentes) {
        List<CdssAlertDto> novos = new ArrayList<>();
        if (base == null || request == null) {
            return novos;
        }
        Medicamento med = base.identificar(request.getMedicamento());
        if (med == null) {
            return novos;
        }
        String prescrito = safe(request.getMedicamento());

        avaliarAlergias(request, med, prescrito, existentes, novos);
        avaliarEmUso(request, med, prescrito, existentes, novos);
        avaliarDose(request, med, existentes, novos);
        avaliarPaciente(request, med, novos);

        if (med.altaVigilancia) {
            novos.add(alerta("CDSS_ALTA_VIGILANCIA", "MINOR", "ALTA_VIGILANCIA", "Medicamento potencialmente perigoso",
                    med.nome + " consta como medicamento de alta vigilancia (ISMP). "
                            + (med.notaGeral == null ? "" : med.notaGeral),
                    "Prescrever concentracao, dose, via e velocidade de forma explicita; a administracao exige dupla checagem.",
                    false, List.of("alta_vigilancia", "ismp")));
        }
        return novos;
    }

    /**
     * Indica se a base tem regra de dose para o medicamento - limite diario, por
     * peso ou "sem teto fixo" justificado. Com regra, a pendencia do bulario sai.
     */
    public boolean possuiDoseMaxima(String medicamento) {
        if (base == null) return false;
        Medicamento med = base.identificar(medicamento);
        return med != null && (med.doseMaximaDiaMg != null || med.doseMaximaMgKgDia != null
                || (med.semTetoFixo != null && !med.semTetoFixo.isBlank()));
    }

    /** Regras de dose da base para o CDSS da tela (mesma fonte do servidor). */
    public List<java.util.Map<String, Object>> regrasDose() {
        List<java.util.Map<String, Object>> regras = new ArrayList<>();
        if (base == null) return regras;
        for (Medicamento m : base.getMedicamentos()) {
            boolean temTeto = m.semTetoFixo != null && !m.semTetoFixo.isBlank();
            if (m.doseMaximaDiaMg == null && m.doseMaximaMgKgDia == null && !temTeto) continue;
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("nome", m.nome);
            r.put("termos", new ArrayList<>(m.termos));
            r.put("doseMaximaDiaMg", m.doseMaximaDiaMg);
            r.put("doseMaximaMgKgDia", m.doseMaximaMgKgDia);
            r.put("semTetoFixo", temTeto ? m.semTetoFixo : null);
            regras.add(r);
        }
        return regras;
    }

    private void avaliarAlergias(CdssAvaliacaoPrescricaoRequest request, Medicamento med, String prescrito,
            List<CdssAlertDto> existentes, List<CdssAlertDto> novos) {
        String prescritoNorm = BaseConhecimentoFarmaceutico.normalizar(prescrito);
        boolean motorJaAlertouAlergia = existe(existentes, "CDSS_ALERGIA_MEDICAMENTO");
        for (String alergiaBruta : lista(request.getAlergias())) {
            String alergia = BaseConhecimentoFarmaceutico.normalizar(alergiaBruta);
            if (alergia.length() < 3) {
                continue;
            }
            // O motor fixo ja cobre o caso "texto da alergia contido no nome prescrito".
            if (prescritoNorm.contains(alergia)) {
                continue;
            }
            // Sinonimo/nome comercial: alergia a "novalgina" e prescricao de "dipirona".
            Medicamento medAlergia = base.identificar(alergiaBruta);
            boolean direta = medAlergia == med
                    || med.termos.stream().anyMatch(t -> t.length() >= 4 && alergia.contains(t));
            if (direta) {
                if (!motorJaAlertouAlergia) {
                    novos.add(alerta("CDSS_ALERGIA_MEDICAMENTO", "CRITICAL", "ALERGIA", "Alergia registrada",
                            "Alergia registrada a " + alergiaBruta.trim() + "; " + prescrito + " contem " + med.nome + ".",
                            "Nao prescrever sem avaliacao e justificativa clinica documentada.", true,
                            List.of("alergia", "sinonimo")));
                    motorJaAlertouAlergia = true;
                }
                continue;
            }

            Set<String> classesAlergia = new LinkedHashSet<>();
            if (medAlergia != null) {
                classesAlergia.addAll(medAlergia.classes);
            }
            classesAlergia.add(alergia);
            for (AlergiaCruzada cruzada : base.getAlergiasCruzadas()) {
                // Mesma comparacao nos dois sentidos da validacao farmaceutica: a
                // triagem registra "penicilina", a base fala em classe.
                boolean alergiaBate = classesAlergia.stream().anyMatch(c -> c.equals(cruzada.classeAlergia)
                        || c.contains(cruzada.classeAlergia)
                        || (c.length() >= 4 && cruzada.classeAlergia.contains(c)));
                if (alergiaBate && med.classes.contains(cruzada.classeMedicamento)) {
                    boolean critico = "CRITICAL".equalsIgnoreCase(cruzada.severidade);
                    novos.add(alerta("CDSS_ALERGIA_CRUZADA", severidade(cruzada.severidade, "MAJOR"), "ALERGIA",
                            "Possivel reatividade cruzada",
                            "Alergia registrada a " + alergiaBruta.trim() + "; " + med.nome
                                    + " pertence a grupo com reatividade cruzada (" + cruzada.grupo + "). "
                                    + safe(cruzada.nota),
                            "Confirmar com o paciente o tipo e a gravidade da reacao anterior; considerar alternativa.",
                            critico, List.of("alergia", "reatividade_cruzada")));
                    break;
                }
            }
        }
    }

    private void avaliarEmUso(CdssAvaliacaoPrescricaoRequest request, Medicamento med, String prescrito,
            List<CdssAlertDto> existentes, List<CdssAlertDto> novos) {
        Set<String> jaAvisados = new LinkedHashSet<>();
        for (String emUsoBruto : lista(request.getMedicamentosAtuais())) {
            Medicamento emUso = base.identificar(emUsoBruto);
            if (emUso == null) {
                continue;
            }
            String atual = emUsoBruto.trim();

            if (emUso == med) {
                if (jaAvisados.add("mesmo:" + med.nome)) {
                    novos.add(alerta("CDSS_MESMO_PRINCIPIO_ATIVO", "MAJOR", "DUPLICIDADE",
                            "Principio ativo ja prescrito",
                            med.nome + " ja esta ativo nesta prescricao (" + atual + ").",
                            "Suspender o item anterior ou confirmar que a associacao e intencional (ex.: dose de resgate).",
                            false, List.of("duplicidade")));
                }
                continue;
            }

            Interacao interacao = buscarInteracao(med, emUso);
            if (interacao != null && !mencionado(existentes, "CDSS_INTERACAO_CRITICA", atual)
                    && jaAvisados.add("int:" + emUso.nome)) {
                boolean critico = "CRITICAL".equalsIgnoreCase(interacao.severidade);
                novos.add(alerta("CDSS_INTERACAO_BASE", severidade(interacao.severidade, "MAJOR"), "INTERACAO",
                        critico ? "Interacao medicamentosa critica" : "Interacao medicamentosa",
                        prescrito + " com " + atual + " (em uso). " + safe(interacao.mecanismo),
                        safe(interacao.conduta), critico, List.of("interacao")));
            }

            for (Duplicidade d : base.getDuplicidades()) {
                if (med.classes.contains(d.classe) && emUso.classes.contains(d.classe)
                        && jaAvisados.add("dup:" + d.classe)) {
                    boolean critico = "CRITICAL".equalsIgnoreCase(d.severidade);
                    novos.add(alerta("CDSS_DUPLICIDADE_TERAPEUTICA", severidade(d.severidade, "MAJOR"), "DUPLICIDADE",
                            "Duplicidade terapeutica",
                            prescrito + " e " + atual + " sao da mesma classe (" + d.rotulo + "). " + safe(d.conduta),
                            "Confirmar se a associacao e intencional antes de salvar.", critico,
                            List.of("duplicidade")));
                }
            }
        }
    }

    private void avaliarDose(CdssAvaliacaoPrescricaoRequest request, Medicamento med, List<CdssAlertDto> existentes,
            List<CdssAlertDto> novos) {
        boolean semPeso = request.getPesoKg() == null || request.getPesoKg() <= 0;
        if (med.exigePeso && semPeso && !existe(existentes, "CDSS_PESO_OBRIGATORIO")
                && !existe(existentes, "CDSS_PESO_PEDIATRICO_OBRIGATORIO")) {
            novos.add(alerta("CDSS_PESO_OBRIGATORIO", "CRITICAL", "DOSE", "Peso obrigatorio para dose segura",
                    med.nome + " tem dose calculada por peso e o peso do paciente nao foi informado.",
                    "Informe o peso antes de liberar a prescricao.", true, List.of("peso")));
        }

        if (existe(existentes, "CDSS_DOSE_MAXIMA_DIA")) {
            return;
        }
        String dose = safe(request.getDose()).toLowerCase(Locale.ROOT);
        Double doseMg = extrairDoseMg(dose);
        if (doseMg == null) {
            return;
        }
        Double tomadas = tomadasPorDia(request.getFrequencia());
        double doseDia = tomadas == null ? doseMg : doseMg * tomadas;
        String prefixo = tomadas == null ? "Dose prescrita de " : "Dose diaria estimada de ";

        boolean porKg = dose.contains("/kg") || dose.contains("/ kg");
        if (porKg) {
            // Dose escrita em mg/kg: compara direto com o limite por peso.
            if (med.doseMaximaMgKgDia != null && doseDia > med.doseMaximaMgKgDia) {
                novos.add(alerta("CDSS_DOSE_MAXIMA_PESO", "CRITICAL", "DOSE", "Dose acima do limite por peso",
                        prefixo + formatar(doseDia) + " mg/kg de " + med.nome + "; limite de referencia: "
                                + formatar(med.doseMaximaMgKgDia) + " mg/kg/dia.",
                        "Ajuste a dose ou registre justificativa clinica para override.", true, List.of("dose", "peso")));
            }
            return;
        }

        if (med.doseMaximaDiaMg != null && doseDia > med.doseMaximaDiaMg) {
            novos.add(alerta("CDSS_DOSE_MAXIMA_DIA", "CRITICAL", "DOSE", "Dose acima do limite diario",
                    prefixo + formatar(doseDia) + " mg de " + med.nome + "; limite de referencia: "
                            + formatar(med.doseMaximaDiaMg) + " mg/dia.",
                    "Ajuste a dose ou registre justificativa clinica para override.", true, List.of("dose")));
            return;
        }
        if (med.doseMaximaMgKgDia != null && !semPeso && doseDia > med.doseMaximaMgKgDia * request.getPesoKg()) {
            double limite = med.doseMaximaMgKgDia * request.getPesoKg();
            novos.add(alerta("CDSS_DOSE_MAXIMA_PESO", "CRITICAL", "DOSE", "Dose acima do limite por peso",
                    prefixo + formatar(doseDia) + " mg de " + med.nome + "; limite para " + formatar(request.getPesoKg())
                            + " kg: " + formatar(limite) + " mg/dia (" + formatar(med.doseMaximaMgKgDia) + " mg/kg/dia).",
                    "Ajuste a dose ou registre justificativa clinica para override.", true, List.of("dose", "peso")));
        }
    }

    private void avaliarPaciente(CdssAvaliacaoPrescricaoRequest request, Medicamento med, List<CdssAlertDto> novos) {
        Double tfg = request.getEgfr();
        if (med.renalLimiteTfg != null && tfg != null && tfg > 0 && tfg < med.renalLimiteTfg) {
            novos.add(alerta("CDSS_AJUSTE_RENAL", "MAJOR", "RENAL", "Ajuste pela funcao renal",
                    "TFG estimada (CKD-EPI) de " + formatar(tfg) + " mL/min/1,73m2, abaixo de " + formatar(med.renalLimiteTfg) + " para "
                            + med.nome + ". " + safe(med.renalConduta),
                    "Ajustar dose/intervalo ou escolher alternativa.", false, List.of("funcao_renal")));
        }

        Integer idade = request.getIdadeAnos();
        if (idade == null) {
            return;
        }
        if (med.idadeMinimaAnos != null && idade < med.idadeMinimaAnos) {
            novos.add(alerta("CDSS_IDADE_MINIMA", "MAJOR", "PEDIATRIA", "Abaixo da idade minima de referencia",
                    med.nome + " tem idade minima de referencia de " + formatarIdade(med.idadeMinimaAnos)
                            + "; paciente com " + idade + " ano(s). " + safe(med.idadeMinimaNota),
                    "Confirmar indicacao e dose pediatrica conforme protocolo institucional.", false,
                    List.of("pediatria", "idade")));
        }
        if (idade >= IDADE_IDOSO && med.beersNota != null && !med.beersNota.isBlank()) {
            novos.add(alerta("CDSS_IDOSO_BEERS", "MINOR", "IDOSO", "Potencialmente inapropriado em idoso (Beers)",
                    "Paciente com " + idade + " anos. " + med.beersNota,
                    "Avaliar alternativa ou menor dose eficaz e monitorar.", false, List.of("idoso", "beers")));
        }
    }

    private Interacao buscarInteracao(Medicamento x, Medicamento y) {
        for (Interacao i : base.getInteracoes()) {
            if (i.porClasse) {
                if ((x.classes.contains(i.a) && y.classes.contains(i.b))
                        || (y.classes.contains(i.a) && x.classes.contains(i.b))) {
                    return i;
                }
            } else if ((bate(x, i.a) && bate(y, i.b)) || (bate(y, i.a) && bate(x, i.b))) {
                return i;
            }
        }
        return null;
    }

    private boolean bate(Medicamento m, String termo) {
        if (termo == null || termo.isBlank()) {
            return false;
        }
        return m.termos.contains(termo) || m.classes.contains(termo)
                || m.termos.stream().anyMatch(t -> t.length() >= 4 && termo.contains(t));
    }

    private Double extrairDoseMg(String dose) {
        Matcher m = DOSE_MASSA.matcher(dose);
        if (!m.find()) {
            return null;
        }
        double valor = Double.parseDouble(m.group(1).replace(',', '.'));
        String unidade = m.group(2).toLowerCase(Locale.ROOT);
        if ("g".equals(unidade)) return valor * 1000d;
        if ("mcg".equals(unidade) || "ug".equals(unidade)) return valor / 1000d;
        return valor;
    }

    /** Tomadas por dia; null quando a frequencia nao permite afirmar (ex.: "se necessario"). */
    private Double tomadasPorDia(String frequencia) {
        if (frequencia == null || frequencia.isBlank()) {
            return null;
        }
        // Normalizacao propria: a da base remove a barra e "8/8h" deixaria de ser reconhecido.
        String valor = Normalizer.normalize(frequencia.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher vezes = VEZES_AO_DIA.matcher(valor);
        if (vezes.find()) {
            double n = Double.parseDouble(vezes.group(1));
            return n > 0 && n <= 24 ? n : null;
        }
        Matcher fracionado = INTERVALO_FRACIONADO.matcher(valor);
        if (fracionado.find()) {
            double horas = Double.parseDouble(fracionado.group(1));
            return horas > 0 && horas <= 24 ? 24d / horas : null;
        }
        Matcher aCada = INTERVALO_A_CADA.matcher(valor);
        if (aCada.find()) {
            double horas = Double.parseDouble(aCada.group(1).replace(',', '.'));
            return horas > 0 && horas <= 24 ? 24d / horas : null;
        }
        if (valor.contains("dose unica") || valor.contains("1x dia") || valor.contains("uma vez ao dia")) {
            return 1d;
        }
        return null;
    }

    private boolean existe(List<CdssAlertDto> alertas, String codigo) {
        return alertas != null && alertas.stream().anyMatch(a -> codigo.equals(a.getCodigo()));
    }

    private boolean mencionado(List<CdssAlertDto> alertas, String codigo, String termo) {
        return alertas != null && alertas.stream()
                .anyMatch(a -> codigo.equals(a.getCodigo()) && safe(a.getMensagem()).contains(termo));
    }

    private CdssAlertDto alerta(String codigo, String severidade, String categoria, String titulo, String mensagem,
            String recomendacao, boolean bloqueante, List<String> fatores) {
        List<String> f = new ArrayList<>(fatores);
        f.add("base_farmaceutica");
        return new CdssAlertDto(codigo, severidade, categoria, titulo, mensagem.trim(), recomendacao, bloqueante, f);
    }

    private String severidade(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.toUpperCase(Locale.ROOT);
    }

    private String formatar(double valor) {
        if (Math.abs(valor - Math.rint(valor)) < 0.01) {
            return String.valueOf((long) Math.rint(valor));
        }
        return String.format(Locale.ROOT, "%.1f", valor).replace('.', ',');
    }

    private String formatarIdade(double anos) {
        if (anos < 1) {
            return Math.round(anos * 12) + " meses";
        }
        return formatar(anos) + " anos";
    }

    private List<String> lista(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
