package br.com.iv.cdss.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.iv.cdss.dto.CdssAlertDto;
import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.cdss.dto.CdssAvaliacaoResponse;
import br.com.iv.cdss.dto.CdssBularioMedicamentoDto;
import br.com.iv.cdss.dto.News2Request;
import br.com.iv.cdss.dto.News2Response;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CdssRulesEngine {
    private static final Pattern DOSE_MG = Pattern.compile("(\\d+(?:[,.]\\d+)?)\\s*(mcg|ug|mg|g)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FREQUENCIA_FRACIONADA_HORAS = Pattern.compile("(\\d+(?:[,.]\\d+)?)\\s*/\\s*\\1\\s*(?:h|hora)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FREQUENCIA_A_CADA_HORAS = Pattern.compile("(?:a\\s*cada|q)\\s*(\\d+(?:[,.]\\d+)?)\\s*(?:h|hora)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, List<String>> INTERACOES_CRITICAS = Map.of(
            "varfarina", List.of("aas", "acido acetilsalicilico", "aspirina", "clopidogrel"),
            "warfarin", List.of("aas", "acido acetilsalicilico", "aspirina", "clopidogrel"),
            "sildenafila", List.of("nitrato", "nitroglicerina", "mononitrato", "isossorbida"),
            "nitroglicerina", List.of("sildenafila"),
            "enalapril", List.of("espironolactona"),
            "espironolactona", List.of("enalapril", "losartana"));
    private static final Map<String, Double> DOSE_MAXIMA_MG_DIA = Map.of(
            "paracetamol", 4000d,
            "dipirona", 6000d,
            "ibuprofeno", 2400d,
            "amoxicilina", 6000d);
    private static final List<String> MEDICAMENTOS_EXIGEM_PESO = List.of("gentamicina", "amicacina", "vancomicina");

    public CdssAvaliacaoResponse avaliarPrescricao(CdssAvaliacaoPrescricaoRequest request) {
        List<CdssAlertDto> alertas = new ArrayList<>();
        String medicamento = normalizar(request == null ? null : request.getMedicamento());
        if (medicamento.isBlank()) {
            alertas.add(alerta("CDSS_MEDICAMENTO_AUSENTE", "MAJOR", "PRESCRICAO", "Medicamento ausente",
                    "Informe o medicamento para avaliar as regras de seguranca.",
                    "Revise a prescricao antes de prosseguir.", false, List.of("medicamento")));
            return resposta(alertas);
        }

        avaliarAlergias(request, medicamento, alertas);
        avaliarInteracoes(request, medicamento, alertas);
        avaliarDose(request, medicamento, alertas);
        avaliarBulario(request, medicamento, alertas);
        alertas.sort(Comparator.comparingInt(a -> ordemSeveridade(a.getSeveridade())));
        return resposta(alertas);
    }

    public News2Response calcularNews2(News2Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados vitais obrigatorios para calcular NEWS2.");
        }
        Map<String, Integer> c = new LinkedHashMap<>();
        c.put("frequenciaRespiratoria", scoreRespiratorio(obrigatorio(request.getFrequenciaRespiratoria(), "frequenciaRespiratoria")));
        c.put("saturacaoOxigenio", scoreSaturacao(obrigatorio(request.getSaturacaoOxigenio(), "saturacaoOxigenio")));
        c.put("oxigenioSuplementar", Boolean.TRUE.equals(request.getOxigenioSuplementar()) ? 2 : 0);
        c.put("temperatura", scoreTemperatura(obrigatorio(request.getTemperatura(), "temperatura")));
        c.put("pressaoSistolica", scorePressao(obrigatorio(request.getPressaoSistolica(), "pressaoSistolica")));
        c.put("frequenciaCardiaca", scoreCardiaco(obrigatorio(request.getFrequenciaCardiaca(), "frequenciaCardiaca")));
        c.put("consciencia", "alerta".equals(normalizar(request.getConsciencia())) || "a".equals(normalizar(request.getConsciencia())) ? 0 : 3);

        int total = c.values().stream().mapToInt(Integer::intValue).sum();
        News2Response response = new News2Response();
        response.setComponentes(c);
        response.setTotal(total);
        response.setRisco(riscoNews2(total, c));
        response.setOrientacao(orientacaoNews2(response.getRisco()));
        return response;
    }

    private void avaliarAlergias(CdssAvaliacaoPrescricaoRequest request, String medicamento, List<CdssAlertDto> alertas) {
        for (String alergia : lista(request.getAlergias())) {
            String alergiaNormalizada = normalizar(alergia);
            if (!alergiaNormalizada.isBlank() && contemTermo(medicamento, alergiaNormalizada)) {
                alertas.add(alerta("CDSS_ALERGIA_MEDICAMENTO", "CRITICAL", "ALERGIA", "Alergia registrada",
                        "Medicamento coincide com alergia registrada: " + alergia + ".",
                        "Nao prescrever sem avaliacao e justificativa clinica documentada.", true, List.of("alergia")));
            }
        }
    }

    private void avaliarInteracoes(CdssAvaliacaoPrescricaoRequest request, String medicamento, List<CdssAlertDto> alertas) {
        for (String atual : lista(request.getMedicamentosAtuais())) {
            String atualNormalizado = normalizar(atual);
            if (interage(medicamento, atualNormalizado)) {
                alertas.add(alerta("CDSS_INTERACAO_CRITICA", "CRITICAL", "INTERACAO", "Interacao medicamentosa critica",
                        "Possivel interacao critica entre " + request.getMedicamento() + " e " + atual + ".",
                        "Reavaliar associacao e registrar justificativa se mantida.", true, List.of("interacao")));
            }
        }
    }

    private void avaliarDose(CdssAvaliacaoPrescricaoRequest request, String medicamento, List<CdssAlertDto> alertas) {
        if (request.getIdadeAnos() != null && request.getIdadeAnos() < 18
                && (request.getPesoKg() == null || request.getPesoKg() <= 0)) {
            alertas.add(alerta("CDSS_PESO_PEDIATRICO_OBRIGATORIO", "CRITICAL", "DOSE", "Peso obrigatorio para validacao pediatrica",
                    "Paciente pediatrico exige peso atual antes da validacao de dose.",
                    "Informe o peso atual e aplique protocolo pediatrico institucional antes de liberar a prescricao.",
                    true, List.of("idade", "peso", "pediatria")));
        }
        if (MEDICAMENTOS_EXIGEM_PESO.stream().anyMatch(medicamento::contains)
                && (request.getPesoKg() == null || request.getPesoKg() <= 0)) {
            alertas.add(alerta("CDSS_PESO_OBRIGATORIO", "CRITICAL", "DOSE", "Peso obrigatorio para dose segura",
                    "Este medicamento exige peso do paciente para validacao da dose.",
                    "Informe o peso antes de liberar a prescricao.", true, List.of("peso")));
        }
        Double limite = DOSE_MAXIMA_MG_DIA.entrySet().stream()
                .filter(e -> medicamento.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (limite == null && request.getBulario() != null) {
            String referencia = safe(request.getBulario().getNome());
            String fonte = safe(request.getBulario().getFonte());
            alertas.add(alerta("CDSS_BULARIO_REGRA_PENDENTE", "MINOR", "BULARIO", "Regra de dose pendente de validacao",
                    "O bulário localizou " + (referencia.isBlank() ? request.getMedicamento() : referencia)
                            + "; a posologia deve ser revisada clinicamente antes de virar regra automática.",
                    fonte.isBlank() ? "Consultar o bulário institucional e validar a regra com farmácia clínica."
                            : "Fonte disponível: " + fonte + ". Validar a regra com farmácia clínica.",
                    false, List.of("bulario", "regra_pendente", "dose")));
        }
        Double doseMg = extrairDoseMg(request.getDose());
        Double doseDiariaMg = doseMg == null ? null : doseDiariaMg(doseMg, request.getFrequencia());
        if (limite != null && doseDiariaMg != null && doseDiariaMg > limite) {
            alertas.add(alerta("CDSS_DOSE_MAXIMA_DIA", "CRITICAL", "DOSE", "Dose acima do limite diario",
                    "Dose diaria estimada: " + doseDiariaMg + " mg; limite de referencia: " + limite + " mg/dia.",
                    "Ajuste a dose ou registre justificativa clinica para override.", true, List.of("dose")));
        }
    }

    private void avaliarBulario(CdssAvaliacaoPrescricaoRequest request, String medicamento, List<CdssAlertDto> alertas) {
        CdssBularioMedicamentoDto bula = request.getBulario();
        if (bula == null) {
            return;
        }
        String bulaNome = safe(bula.getNome());
        String principioAtivo = safe(bula.getPrincipioAtivo());
        String referencia = !bulaNome.isBlank() ? bulaNome : request.getMedicamento();
        String interacoes = normalizar(bula.getInteracoes());
        for (String atual : lista(request.getMedicamentosAtuais())) {
            String atualNormalizado = normalizar(atual);
            if (!atualNormalizado.isBlank() && contemTermo(interacoes, atualNormalizado)) {
                alertas.add(alerta("CDSS_BULARIO_INTERACAO", "MAJOR", "BULARIO", "Interacao descrita no bulario",
                        "O bulario de " + referencia + " cita possivel interacao com " + atual + ".",
                        "Conferir a bula institucional e avaliar ajuste, monitoramento ou substituicao.", false,
                        List.of("bulario", "interacao")));
            }
        }
        String contraindicacoes = normalizar(bula.getContraindicacoes());
        for (String alergia : lista(request.getAlergias())) {
            String alergiaNormalizada = normalizar(alergia);
            if (!alergiaNormalizada.isBlank()
                    && (contemTermo(contraindicacoes, alergiaNormalizada)
                            || (!principioAtivo.isBlank() && contemTermo(alergiaNormalizada, normalizar(principioAtivo))))) {
                alertas.add(alerta("CDSS_BULARIO_CONTRAINDICACAO", "CRITICAL", "BULARIO", "Contraindicacao no bulario",
                        "O bulario de " + referencia + " contem contraindicao relacionada a " + alergia + ".",
                        "Nao prescrever sem avaliacao e justificativa clinica documentada.", true,
                        List.of("bulario", "contraindicacao", "alergia")));
            }
        }
        String posologia = normalizar(bula.getPosologia());
        if (!posologia.isBlank() && contemTermo(posologia, "peso") && contemTermo(posologia, "mg/kg")
                && (request.getPesoKg() == null || request.getPesoKg() <= 0)) {
            alertas.add(alerta("CDSS_BULARIO_PESO_OBRIGATORIO", "CRITICAL", "BULARIO", "Peso exigido pela posologia",
                    "A posologia do bulario de " + referencia + " indica calculo por peso.",
                    "Informe o peso antes de liberar a prescricao.", true, List.of("bulario", "peso", "posologia")));
        }
    }

    private boolean interage(String medicamento, String atual) {
        return INTERACOES_CRITICAS.entrySet().stream().anyMatch(e ->
                contemTermo(medicamento, e.getKey()) && e.getValue().stream().anyMatch(v -> contemTermo(atual, v))
                        || contemTermo(atual, e.getKey()) && e.getValue().stream().anyMatch(v -> contemTermo(medicamento, v)));
    }

    private boolean contemTermo(String texto, String termo) {
        return texto != null && termo != null && texto.contains(termo);
    }

    private Double extrairDoseMg(String dose) {
        Matcher matcher = DOSE_MG.matcher(safe(dose));
        if (!matcher.find()) {
            return null;
        }
        double valor = Double.parseDouble(matcher.group(1).replace(',', '.'));
        String unidade = matcher.group(2).toLowerCase(Locale.ROOT);
        if ("g".equals(unidade)) return valor * 1000d;
        if ("mcg".equals(unidade) || "ug".equals(unidade)) return valor / 1000d;
        return valor;
    }

    private Double doseDiariaMg(Double doseMg, String frequencia) {
        Double intervaloHoras = extrairIntervaloHoras(frequencia);
        return intervaloHoras == null ? doseMg : doseMg * (24d / intervaloHoras);
    }

    private Double extrairIntervaloHoras(String frequencia) {
        String valor = normalizar(frequencia).replace(',', '.');
        Matcher fracionada = FREQUENCIA_FRACIONADA_HORAS.matcher(valor);
        Matcher aCada = FREQUENCIA_A_CADA_HORAS.matcher(valor);
        String intervalo = fracionada.find() ? fracionada.group(1) : (aCada.find() ? aCada.group(1) : null);
        if (intervalo == null) return null;
        double horas = Double.parseDouble(intervalo.replace(',', '.'));
        return horas > 0d && horas <= 24d ? horas : null;
    }

    private CdssAvaliacaoResponse resposta(List<CdssAlertDto> alertas) {
        CdssAvaliacaoResponse response = new CdssAvaliacaoResponse();
        response.setAlertas(alertas);
        response.setPossuiAlertaCritico(alertas.stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase(a.getSeveridade())));
        response.setBloqueante(alertas.stream().anyMatch(CdssAlertDto::isBloqueante));
        return response;
    }

    private CdssAlertDto alerta(String codigo, String severidade, String categoria, String titulo, String mensagem,
            String recomendacao, boolean bloqueante, List<String> fatores) {
        return new CdssAlertDto(codigo, severidade, categoria, titulo, mensagem, recomendacao, bloqueante, fatores);
    }

    private int ordemSeveridade(String severidade) {
        return switch (safe(severidade).toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "MAJOR" -> 1;
            default -> 2;
        };
    }

    private int scoreRespiratorio(int v) {
        if (v <= 8) return 3;
        if (v <= 11) return 1;
        if (v <= 20) return 0;
        if (v <= 24) return 2;
        return 3;
    }

    private int scoreSaturacao(int v) {
        if (v <= 91) return 3;
        if (v <= 93) return 2;
        if (v <= 95) return 1;
        return 0;
    }

    private int scoreTemperatura(double v) {
        if (v <= 35.0) return 3;
        if (v <= 36.0) return 1;
        if (v <= 38.0) return 0;
        if (v <= 39.0) return 1;
        return 2;
    }

    private int scorePressao(int v) {
        if (v <= 90) return 3;
        if (v <= 100) return 2;
        if (v <= 110) return 1;
        if (v <= 219) return 0;
        return 3;
    }

    private int scoreCardiaco(int v) {
        if (v <= 40) return 3;
        if (v <= 50) return 1;
        if (v <= 90) return 0;
        if (v <= 110) return 1;
        if (v <= 130) return 2;
        return 3;
    }

    private String riscoNews2(int total, Map<String, Integer> componentes) {
        if (total >= 7) return "ALTO";
        if (total >= 5 || componentes.values().stream().anyMatch(score -> score == 3)) return "MEDIO";
        if (total >= 1) return "BAIXO_MEDIO";
        return "BAIXO";
    }

    private String orientacaoNews2(String risco) {
        return switch (risco) {
            case "ALTO" -> "Acionar avaliacao clinica urgente e considerar cuidado intensivo conforme protocolo institucional.";
            case "MEDIO" -> "Priorizar reavaliacao clinica e aumentar frequencia de monitoramento.";
            case "BAIXO_MEDIO" -> "Manter monitoramento e reavaliar se houver piora clinica.";
            default -> "Manter rotina assistencial conforme protocolo.";
        };
    }

    private <T> T obrigatorio(T valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Campo obrigatorio para NEWS2: " + campo);
        }
        return valor;
    }

    private List<String> lista(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String normalizar(String value) {
        String normalized = Normalizer.normalize(safe(value).toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
