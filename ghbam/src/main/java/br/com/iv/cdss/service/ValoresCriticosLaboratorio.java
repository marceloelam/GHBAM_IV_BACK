package br.com.iv.cdss.service;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Limites de valor critico (panico) dos exames laboratoriais. A tabela fica em
 * {@code resources/laboratorio/valores-criticos.json} para o laboratorio e a
 * direcao clinica ajustarem sem mexer na logica.
 *
 * Conservador: so acusa quando reconhece o exame (LOINC ou nome exato), consegue
 * ler o numero e a unidade e compativel. Na duvida, nao acusa - o laudo continua
 * sendo lido pelo profissional como sempre.
 */
@ApplicationScoped
public class ValoresCriticosLaboratorio {
    public static final String RECURSO = "/laboratorio/valores-criticos.json";
    private static final Pattern NUMERO = Pattern.compile("\\d[\\d.,]*");
    private static final Pattern MILHAR_PONTO = Pattern.compile("\\d{1,3}(\\.\\d{3})+");
    private static final Pattern MILHAR_VIRGULA = Pattern.compile("\\d{1,3}(,\\d{3})+");

    public static class Analito {
        public String nome;
        public Set<String> loinc = new LinkedHashSet<>();
        public Set<String> nomes = new LinkedHashSet<>();
        public Set<String> unidades = new LinkedHashSet<>();
        public boolean contagem;
        public Double limiteMilhar;
        public Double baixo;
        public Double alto;
    }

    /** Resultado critico encontrado em um item do laudo. */
    public record Achado(String analito, String direcao, double limite, double valorNumerico) {
    }

    private final List<Analito> analitos = new ArrayList<>();
    private String versao;

    @PostConstruct
    void carregar() {
        try (InputStream in = ValoresCriticosLaboratorio.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                System.err.println("[ValorCritico] Tabela nao encontrada em " + RECURSO + "; nenhum valor critico sera acusado.");
                return;
            }
            JsonNode raiz = new ObjectMapper().readTree(in);
            versao = raiz.path("versao").asText(null);
            for (JsonNode no : raiz.path("analitos")) {
                Analito a = new Analito();
                a.nome = no.path("nome").asText();
                no.path("loinc").forEach(v -> a.loinc.add(v.asText().trim()));
                no.path("nomes").forEach(v -> a.nomes.add(normalizarNome(v.asText())));
                no.path("unidades").forEach(v -> a.unidades.add(normalizarUnidade(v.asText())));
                a.contagem = no.path("contagem").asBoolean(false);
                a.limiteMilhar = no.hasNonNull("limiteMilhar") ? no.get("limiteMilhar").asDouble() : null;
                a.baixo = no.hasNonNull("baixo") ? no.get("baixo").asDouble() : null;
                a.alto = no.hasNonNull("alto") ? no.get("alto").asDouble() : null;
                analitos.add(a);
            }
            System.out.println("[ValorCritico] Tabela " + versao + " carregada: " + analitos.size() + " exames.");
        } catch (Exception e) {
            System.err.println("[ValorCritico] Falha ao carregar a tabela de valores criticos: " + e.getMessage());
        }
    }

    public String getVersao() { return versao; }

    public Achado avaliar(String nomeExame, String codigoLoinc, String valor, String unidadeMedida) {
        Analito analito = identificar(nomeExame, codigoLoinc);
        if (analito == null) {
            return null;
        }
        String unidade = normalizarUnidade(unidadeMedida);
        if (!analito.contagem && !analito.unidades.isEmpty() && !unidade.isBlank()
                && analito.unidades.stream().noneMatch(unidade::equals)) {
            return null; // unidade diferente (ex.: glicose em mmol/L): nao compara
        }
        Double numero = lerNumero(valor, analito.contagem);
        if (numero == null) {
            return null;
        }
        if (analito.contagem) {
            numero = escalarContagem(numero, unidade, analito.limiteMilhar);
        }
        if (analito.baixo != null && numero < analito.baixo) {
            return new Achado(analito.nome, "BAIXO", analito.baixo, numero);
        }
        if (analito.alto != null && numero > analito.alto) {
            return new Achado(analito.nome, "ALTO", analito.alto, numero);
        }
        return null;
    }

    private Analito identificar(String nomeExame, String codigoLoinc) {
        String loinc = codigoLoinc == null ? "" : codigoLoinc.trim();
        if (!loinc.isBlank()) {
            for (Analito a : analitos) {
                if (a.loinc.contains(loinc)) return a;
            }
            return null; // LOINC conhecido de outro exame: o nome nao deve sobrepor
        }
        String nome = normalizarNome(nomeExame);
        if (nome.isBlank()) return null;
        for (Analito a : analitos) {
            if (a.nomes.contains(nome)) return a;
        }
        return null;
    }

    static Double lerNumero(String valor, boolean contagem) {
        if (valor == null) return null;
        Matcher m = NUMERO.matcher(valor);
        if (!m.find()) return null;
        String bruto = m.group().replaceAll("[.,]+$", "");
        if (bruto.isBlank()) return null;
        boolean temPonto = bruto.contains(".");
        boolean temVirgula = bruto.contains(",");
        String limpo;
        if (temPonto && temVirgula) {
            // O ultimo separador e o decimal.
            limpo = bruto.lastIndexOf(',') > bruto.lastIndexOf('.')
                    ? bruto.replace(".", "").replace(',', '.')
                    : bruto.replace(",", "");
        } else if (temVirgula) {
            limpo = contagem && MILHAR_VIRGULA.matcher(bruto).matches() ? bruto.replace(",", "") : bruto.replace(',', '.');
        } else if (temPonto) {
            limpo = contagem && MILHAR_PONTO.matcher(bruto).matches() ? bruto.replace(".", "") : bruto;
        } else {
            limpo = bruto;
        }
        try {
            return Double.parseDouble(limpo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Leva a contagem do hemograma para unidades por mm3 (= por uL). */
    static double escalarContagem(double numero, String unidade, Double limiteMilhar) {
        boolean emMilhares = unidade.contains("mil") || unidade.contains("10^3") || unidade.contains("103")
                || unidade.contains("10e3") || unidade.contains("10^9") || unidade.contains("109")
                || unidade.startsWith("k/");
        if (emMilhares) return numero * 1000d;
        // Sem unidade, numero pequeno so pode estar em milhares (plaquetas "180" = 180.000).
        if (unidade.isBlank() && limiteMilhar != null && numero < limiteMilhar) return numero * 1000d;
        return numero;
    }

    static String normalizarNome(String texto) {
        String base = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\s:.]+$", "")
                .replaceAll("\\s*[,(-]?\\s*\\(?(soro|serico|serica|sangue|plasma)\\)?$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalizarUnidade(String texto) {
        String base = texto == null ? "" : texto.toLowerCase(Locale.ROOT)
                .replace("µ", "u").replace("μ", "u").replace("³", "3").replace("⁹", "9");
        return Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", "")
                .trim();
    }
}
