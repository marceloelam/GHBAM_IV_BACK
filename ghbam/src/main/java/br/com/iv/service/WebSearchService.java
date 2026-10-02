package br.com.iv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class WebSearchService {

    private static final Logger LOG = Logger.getLogger(WebSearchService.class);

    private static final String DDG_API = "https://api.duckduckgo.com/?q=%s&format=json&no_html=1&skip_disambig=1";
    private static final int MAX_RESULTS = 5;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String buscar(String query) {
        if (query == null || query.isBlank()) return "";
        try {
            String resposta = buscarDuckDuckGo(query);
            if (!resposta.isBlank()) return resposta;
        } catch (Exception e) {
            LOG.warn("WebSearch (DuckDuckGo) falhou: " + e.getMessage());
        }
        return "";
    }

    private String buscarDuckDuckGo(String query) throws Exception {
        String url = String.format(DDG_API, URLEncoder.encode(query, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "GHBAM-IV-Medical/1.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return "";

        return extrairResultados(resp.body());
    }

    private String extrairResultados(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            StringBuilder sb = new StringBuilder();

            JsonNode abstractText = root.get("AbstractText");
            if (abstractText != null && !abstractText.asText().isBlank()) {
                sb.append("Resumo: ").append(abstractText.asText()).append("\n\n");
            }

            JsonNode heading = root.get("Heading");
            if (heading != null && !heading.asText().isBlank()) {
                sb.append("Topico: ").append(heading.asText()).append("\n\n");
            }

            JsonNode definition = root.get("Definition");
            if (definition != null && !definition.asText().isBlank()) {
                sb.append("Definicao: ").append(definition.asText()).append("\n\n");
            }

            JsonNode source = root.get("AbstractSource");
            if (source != null && !source.asText().isBlank()) {
                sb.append("Fonte: ").append(source.asText());
                JsonNode url = root.get("AbstractURL");
                if (url != null && !url.asText().isBlank()) {
                    sb.append(" (").append(url.asText()).append(")");
                }
                sb.append("\n\n");
            }

            JsonNode related = root.get("RelatedTopics");
            if (related != null && related.isArray()) {
                sb.append("Topicos relacionados:\n");
                int count = 0;
                for (JsonNode topic : related) {
                    if (count >= MAX_RESULTS) break;
                    if (topic.has("Text")) {
                        sb.append("- ").append(topic.get("Text").asText()).append("\n");
                        count++;
                    } else if (topic.has("Topics") && topic.get("Topics").isArray()) {
                        for (JsonNode sub : topic.get("Topics")) {
                            if (count >= MAX_RESULTS) break;
                            if (sub.has("Text")) {
                                sb.append("- ").append(sub.get("Text").asText()).append("\n");
                                count++;
                            }
                            if (sub.has("FirstURL")) {
                                sb.append("  ").append(sub.get("FirstURL").asText()).append("\n");
                            }
                        }
                    }
                }
            }

            JsonNode results = root.get("Results");
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    if (r.has("Text")) {
                        sb.append("- ").append(r.get("Text").asText()).append("\n");
                    }
                    if (r.has("FirstURL")) {
                        sb.append("  ").append(r.get("FirstURL").asText()).append("\n");
                    }
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            LOG.warn("Erro ao extrair resultados do DuckDuckGo: " + e.getMessage());
            return "";
        }
    }
}
