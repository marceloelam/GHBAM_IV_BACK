package br.com.iv.service;

import br.gov.secure.AppSecrets;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

    private static final String EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2";
    private static final String HF_EMBEDDING_URL =
        "https://router.huggingface.co/hf-inference/models/" + EMBEDDING_MODEL + "/pipeline/feature-extraction";

    private static final int CACHE_MAX_SIZE = 500;

    @ConfigProperty(name = "rag.embedding.local.url", defaultValue = "http://localhost:8001/embed")
    String localEmbeddingUrl;

    @ConfigProperty(name = "rag.embedding.local.timeout.seconds", defaultValue = "5")
    int localEmbeddingTimeoutSeconds;

    @ConfigProperty(name = "rag.embedding.hf.timeout.seconds", defaultValue = "10")
    int hfEmbeddingTimeoutSeconds;

    private final HttpClient httpClient;
    private final LinkedHashMap<String, float[]> embeddingCache;

    public EmbeddingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.embeddingCache = new LinkedHashMap<String, float[]>(CACHE_MAX_SIZE + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };
    }

    public float[] gerarEmbedding(String texto) {
        if (texto == null || texto.isBlank()) return null;

        String chave = texto.trim().toLowerCase();
        synchronized (embeddingCache) {
            if (embeddingCache.containsKey(chave)) {
                LOG.debug("Embedding cache hit");
                return embeddingCache.get(chave);
            }
        }

        float[] embedding = null;
        try {
            embedding = chamarEmbeddingLocal(texto);
        } catch (Exception e) {
            LOG.warn("Servico local de embedding indisponivel em " + localEmbeddingUrl + ": " + detalharErro(e));
        }
        if (embedding == null) {
            try {
                embedding = chamarEmbeddingHF(texto);
            } catch (Exception e) {
                LOG.warn("Todas as fontes de embedding falharam: " + detalharErro(e));
            }
        }

        if (embedding != null) {
            synchronized (embeddingCache) {
                embeddingCache.put(chave, embedding);
            }
        }
        return embedding;
    }

    private float[] chamarEmbeddingLocal(String texto) throws Exception {
        String jsonRequest = "{\"texto\":\"" + escapeJson(texto) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(localEmbeddingUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .timeout(Duration.ofSeconds(localEmbeddingTimeoutSeconds))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Servico local de embedding: " + response.statusCode() + " - " + response.body());
        }

        return parseEmbedding(response.body());
    }

    private float[] chamarEmbeddingHF(String texto) throws Exception {
        if (AppSecrets.HF_API_TOKEN == null || AppSecrets.HF_API_TOKEN.isBlank()) {
            throw new IllegalStateException("HF_API_TOKEN nao configurado em AppSecrets");
        }

        String jsonRequest = "{\"inputs\":\"" + escapeJson(texto) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_EMBEDDING_URL))
                .header("Authorization", "Bearer " + AppSecrets.HF_API_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .timeout(Duration.ofSeconds(hfEmbeddingTimeoutSeconds))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API de embedding HuggingFace: " + response.statusCode() + " - " + response.body());
        }

        return parseEmbedding(response.body());
    }

    private float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) {
            throw new RuntimeException("Resposta vazia da API de embedding");
        }
        json = json.trim();
        if (json.startsWith("[[")) {
            json = json.substring(1, json.length() - 1);
        }
        if (json.startsWith("[")) {
            String[] parts = json.substring(1, json.length() - 1).split(",");
            float[] embedding = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                embedding[i] = Float.parseFloat(parts[i].trim());
            }
            return embedding;
        }
        throw new RuntimeException("Formato de embedding inesperado: " + json.substring(0, Math.min(100, json.length())));
    }

    public String embeddingParaString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String detalharErro(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + message;
    }
}
