package br.com.iv.iacodificacao;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Inferencia do modelo treinado por tools/ia-codificacao/treinar_codificacao.py
 * (TF-IDF unigrama+bigrama, tf sublinear, L2 + regressao logistica um-contra-todos).
 * Sem dependencia externa: so produtos escalares. A normalizacao do texto precisa
 * ser identica a do script Python (funcao normalizar).
 */
public final class ModeloCodificacao {

    public record Sugestao(String codigo, String descricao, double probabilidade) {
    }

    /** Uma cabeca de classificacao (CID ou procedimento). */
    static final class Cabeca {
        final Map<String, Integer> termos = new HashMap<>();
        double[] idf;
        String[] codigos;
        String[] descricoes;
        double[] interceptos;
        int[][] indices;
        double[][] pesos;
        JsonNode metricas;
        JsonNode registrosTreino;
        JsonNode amostras;
    }

    private final Map<String, Cabeca> cabecas = new LinkedHashMap<>();
    private final String geradoEm;
    private final String periodo;

    public ModeloCodificacao(InputStream json) throws IOException {
        JsonNode raiz = new ObjectMapper().readTree(json);
        geradoEm = raiz.path("gerado_em").asText("");
        JsonNode p = raiz.path("periodo");
        periodo = p.isArray() && p.size() == 2 ? p.get(0).asText() + " a " + p.get(1).asText() : "";
        raiz.path("modelos").fields().forEachRemaining(e -> cabecas.put(e.getKey(), ler(e.getValue())));
    }

    private static Cabeca ler(JsonNode m) {
        Cabeca c = new Cabeca();
        JsonNode termos = m.path("termos");
        JsonNode idf = m.path("idf");
        c.idf = new double[termos.size()];
        for (int i = 0; i < termos.size(); i++) {
            c.termos.put(termos.get(i).asText(), i);
            c.idf[i] = idf.get(i).asDouble();
        }
        JsonNode classes = m.path("classes");
        int n = classes.size();
        c.codigos = new String[n];
        c.descricoes = new String[n];
        c.interceptos = new double[n];
        c.indices = new int[n][];
        c.pesos = new double[n][];
        for (int i = 0; i < n; i++) {
            JsonNode cl = classes.get(i);
            c.codigos[i] = cl.path("codigo").asText();
            c.descricoes[i] = cl.path("descricao").asText("");
            c.interceptos[i] = cl.path("intercepto").asDouble();
            JsonNode pw = cl.path("pesos");
            int[] idx = new int[pw.size()];
            double[] w = new double[pw.size()];
            int k = 0;
            for (var it = pw.fields(); it.hasNext(); k++) {
                var e = it.next();
                idx[k] = Integer.parseInt(e.getKey());
                w[k] = e.getValue().asDouble();
            }
            c.indices[i] = idx;
            c.pesos[i] = w;
        }
        c.metricas = m.path("metricas");
        c.registrosTreino = m.path("registros_treino");
        c.amostras = m.path("amostras_verificacao");
        return c;
    }

    /** Mesma regra de treinar_codificacao.normalizar. */
    static String normalizar(String t) {
        String s = Normalizer.normalize(t == null ? "" : t.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFKD);
        s = s.replaceAll("\\p{M}+", "");
        s = s.replaceAll("[0-9]+", " ");
        return s.replaceAll("[^a-z ]+", " ");
    }

    /** Tokens do scikit-learn com token_pattern \b[a-z][a-z]+\b, mais bigramas consecutivos. */
    static List<String> ngramas(String textoNormalizado) {
        List<String> tokens = new ArrayList<>();
        for (String t : textoNormalizado.trim().split(" +")) {
            if (t.length() >= 2) tokens.add(t);
        }
        List<String> r = new ArrayList<>(tokens);
        for (int i = 0; i + 1 < tokens.size(); i++) {
            r.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return r;
    }

    public List<Sugestao> sugerir(String modelo, String texto, int limite) {
        Cabeca c = cabecas.get(modelo);
        if (c == null) return List.of();
        Map<Integer, Integer> tf = new HashMap<>();
        for (String g : ngramas(normalizar(texto))) {
            Integer j = c.termos.get(g);
            if (j != null) tf.merge(j, 1, Integer::sum);
        }
        if (tf.isEmpty()) return List.of();
        Map<Integer, Double> x = new HashMap<>();
        double norma = 0;
        for (var e : tf.entrySet()) {
            double v = (1 + Math.log(e.getValue())) * c.idf[e.getKey()];
            x.put(e.getKey(), v);
            norma += v * v;
        }
        norma = Math.sqrt(norma);
        for (var e : x.entrySet()) e.setValue(e.getValue() / norma);

        int n = c.codigos.length;
        double[] prob = new double[n];
        double soma = 0;
        for (int i = 0; i < n; i++) {
            double z = c.interceptos[i];
            int[] idx = c.indices[i];
            double[] w = c.pesos[i];
            for (int k = 0; k < idx.length; k++) {
                Double v = x.get(idx[k]);
                if (v != null) z += w[k] * v;
            }
            prob[i] = 1.0 / (1.0 + Math.exp(-z));
            soma += prob[i];
        }
        Integer[] ordem = new Integer[n];
        for (int i = 0; i < n; i++) ordem[i] = i;
        java.util.Arrays.sort(ordem, (a, b) -> Double.compare(prob[b], prob[a]));
        List<Sugestao> r = new ArrayList<>();
        for (int k = 0; k < Math.min(limite, n); k++) {
            int i = ordem[k];
            r.add(new Sugestao(c.codigos[i], c.descricoes[i], soma > 0 ? prob[i] / soma : 0));
        }
        return r;
    }

    public String geradoEm() {
        return geradoEm;
    }

    public String periodo() {
        return periodo;
    }

    /**
     * Metricas do teste temporal gravadas pelo script, por origem do texto
     * (laudo, alta, internacao): top1/top3/top5, registros_teste, teste_desde.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> metricas(String modelo) {
        Cabeca c = cabecas.get(modelo);
        Map<String, Object> m = new LinkedHashMap<>();
        if (c == null) return m;
        ObjectMapper om = new ObjectMapper();
        if (c.metricas.isObject()) m.putAll(om.convertValue(c.metricas, LinkedHashMap.class));
        if (c.registrosTreino.isObject()) m.put("registros_treino", om.convertValue(c.registrosTreino, LinkedHashMap.class));
        return m;
    }

    /** Tira marcacao HTML do editor rico (relato da alta) antes de normalizar. */
    static String semHtml(String t) {
        if (t == null) return "";
        return t.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
    }

    JsonNode amostras(String modelo) {
        Cabeca c = cabecas.get(modelo);
        return c == null ? null : c.amostras;
    }
}
