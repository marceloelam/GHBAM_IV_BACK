package br.com.iv.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Le laudo e ficha SINAN como evidencia e, quando seguro, sugere uma classificacao. Nunca grava: quem decide e a
 * vigilancia. IgG reagente pode ser infeccao antiga e nao sugere nada; exame negativo tambem nao sugere descarte,
 * porque pode ter sido colhido antes da janela. Listas de exames e sinonimos a validar com o laboratorio.
 */
public final class EvidenciaCasoEpidemiologico {

    private EvidenciaCasoEpidemiologico() {}

    public static final String LABORATORIO = "LABORATORIO";
    public static final String SINAN = "SINAN";
    public static final String REAGENTE = "REAGENTE";
    public static final String NAO_REAGENTE = "NAO_REAGENTE";
    public static final String VER_LAUDO = "VER_LAUDO";
    public static final String CONFIRMADO = ClassificacaoCasoEpidemiologico.CONFIRMADO;
    public static final String DESCARTADO = ClassificacaoCasoEpidemiologico.DESCARTADO;

    public record Evidencia(String origem, String descricao, String resultado, String criterio) {}
    /** classificacao vazia = evidencias divergentes: o motivo explica e nada e sugerido. */
    public record Sugestao(String classificacao, String criterio, String motivo) {}

    private static final Map<String, List<String>> SINONIMOS_EXAME = Map.of(
            "dengue", List.of("ns1"),
            "tuberculose", List.of("baar", "baciloscopia", "teste molecular", "trm tb"),
            "leptospirose", List.of("leptospira"),
            "influenza", List.of("sars cov 2", "covid", "virus respiratorios"),
            "gripe", List.of("influenza", "sars cov 2", "covid", "virus respiratorios"),
            "srag", List.of("influenza", "sars cov 2", "covid", "virus respiratorios"),
            "sindrome gripal", List.of("influenza", "sars cov 2", "covid", "virus respiratorios"));
    private static final List<String> NEGATIVOS = List.of("nao reagente", "negativo", "negativa", "nao detectado", "nao detectavel", "indetectavel", "ausente");
    private static final List<String> POSITIVOS = List.of("reagente", "positivo", "positiva", "detectado", "detectavel", "presente");
    private static final Set<String> SEM_CLASSIFICACAO_FINAL = Set.of("clinica", "etiologica");

    private static String normalizar(Object valor) {
        String semAcento = Normalizer.normalize(valor == null ? "" : String.valueOf(valor), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return " " + semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    private static boolean contem(String textoNormalizado, List<String> termos) {
        return termos.stream().anyMatch(termo -> textoNormalizado.contains(" " + termo + " "));
    }

    private static boolean citaRegra(String textoNormalizado, String nomeRegra, String termosRegra, boolean comSinonimos) {
        List<String> termos = new ArrayList<>(DefinicaoCasoEpidemiologico.termosEstruturados(nomeRegra, termosRegra));
        if (comSinonimos) {
            for (String termo : List.copyOf(termos)) termos.addAll(SINONIMOS_EXAME.getOrDefault(termo, List.of()));
        }
        return contem(textoNormalizado, termos);
    }

    /** null quando o exame nao tem relacao com a doenca da regra. */
    public static Evidencia doExame(String nomeExame, String valor, String nomeRegra, String termosRegra) {
        String nome = normalizar(nomeExame);
        if (!citaRegra(nome, nomeRegra, termosRegra, true)) return null;
        String texto = normalizar(valor);
        String resultado = VER_LAUDO;
        // Negativos antes: "nao reagente" tambem contem "reagente".
        if (contem(texto, NEGATIVOS)) resultado = NAO_REAGENTE;
        else if (contem(texto, POSITIVOS) && !nome.contains(" igg ")) resultado = REAGENTE;
        String descricao = String.valueOf(nomeExame).trim() + (valor == null || valor.isBlank() ? ": ver laudo" : ": " + valor.trim());
        return new Evidencia(LABORATORIO, descricao, resultado, REAGENTE.equals(resultado) ? "LABORATORIAL" : "");
    }

    /** null sem relacao com a regra ou sem classificacao final preenchida. */
    public static Evidencia daFichaSinan(String tipo, Map<String, Object> dados, String nomeRegra, String termosRegra) {
        if (dados == null) return null;
        if (!citaRegra(normalizar(tipo + " " + dados.getOrDefault("agravo", "")), nomeRegra, termosRegra, false)) return null;
        String resultado = null;
        String valorFicha = "";
        String criterio = "";
        for (Map.Entry<String, Object> campo : dados.entrySet()) {
            String chave = campo.getKey().toLowerCase(Locale.ROOT);
            String valor = campo.getValue() instanceof String texto ? texto.trim() : "";
            if (valor.isEmpty()) continue;
            if (chave.contains("criterio")) criterio = criterioDaFicha(valor);
            if (!chave.contains("classificacao") || SEM_CLASSIFICACAO_FINAL.stream().anyMatch(chave::contains)) continue;
            String texto = normalizar(valor);
            if (texto.contains(" descartad")) resultado = DESCARTADO;
            else if (texto.contains(" confirmad") || ("classificacaodengue".equals(chave) && valor.matches("^1[0-3]\\b.*"))) resultado = CONFIRMADO;
            else continue;
            valorFicha = valor;
        }
        if (resultado == null) return null;
        return new Evidencia(SINAN, "Ficha SINAN: " + valorFicha, resultado, CONFIRMADO.equals(resultado) ? criterio : "");
    }

    private static String criterioDaFicha(String valor) {
        String texto = normalizar(valor);
        if (texto.contains(" laborat")) return "LABORATORIAL";
        if (texto.contains(" epidemiol")) return "CLINICO_EPIDEMIOLOGICO";
        if (texto.contains(" clinic")) return "CLINICO";
        return "";
    }

    /** null quando nao ha o que sugerir. */
    public static Sugestao sugerir(List<Evidencia> evidencias) {
        Evidencia reagente = evidencias.stream().filter(e -> REAGENTE.equals(e.resultado())).findFirst().orElse(null);
        Evidencia fichaConfirma = evidencias.stream().filter(e -> SINAN.equals(e.origem()) && CONFIRMADO.equals(e.resultado())).findFirst().orElse(null);
        Evidencia fichaDescarta = evidencias.stream().filter(e -> SINAN.equals(e.origem()) && DESCARTADO.equals(e.resultado())).findFirst().orElse(null);
        if (fichaDescarta != null && (reagente != null || fichaConfirma != null)) {
            return new Sugestao("", "", "Evidências divergentes: confira o laudo e a ficha SINAN.");
        }
        if (reagente != null) return new Sugestao(CONFIRMADO, "LABORATORIAL", reagente.descricao());
        if (fichaConfirma != null) return new Sugestao(CONFIRMADO, fichaConfirma.criterio(), fichaConfirma.descricao());
        if (fichaDescarta != null) return new Sugestao(DESCARTADO, "", fichaDescarta.descricao());
        return null;
    }

    /** Campos enviados a tela: a ficha completa nunca sai do servidor. */
    public static Map<String, Object> paraTela(Evidencia evidencia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("origem", evidencia.origem());
        m.put("descricao", evidencia.descricao());
        m.put("resultado", evidencia.resultado());
        return m;
    }

    public static Map<String, Object> paraTela(Sugestao sugestao) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classificacao", sugestao.classificacao());
        m.put("criterio", sugestao.criterio());
        m.put("motivo", sugestao.motivo());
        return m;
    }
}
