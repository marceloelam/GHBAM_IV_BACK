package br.com.iv.imagemia.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Cliente HTTP do servico de inferencia MONAI (Python/FastAPI, interno a rede do
 * GHBAM, mesmo modelo do CompreFace e do Piper). Envia os bytes DICOM e recebe
 * um achado sugerido em JSON. Nunca lanca para fora: falha vira ResultadoMonai
 * com erro preenchido, para o fluxo clinico nunca quebrar por causa da IA.
 */
@ApplicationScoped
public class ImagemIaClient {

    private static final Logger LOG = Logger.getLogger(ImagemIaClient.class);

    @ConfigProperty(name = "ghbam.monai.url")
    Optional<String> url;

    @ConfigProperty(name = "ghbam.monai.timeout.seconds", defaultValue = "120")
    long timeoutSeconds;

    /** TC: volume inteiro em CPU leva minutos (nodulo pulmonar: ~10 min em 8 nucleos). Abaixo dos 30 min do PENDENTE. */
    @ConfigProperty(name = "ghbam.monai.timeout.tc.seconds", defaultValue = "1500")
    long timeoutTcSeconds;

    @Inject
    ObjectMapper objectMapper;

    // HTTP/1.1 explicito: o POST de corpo grande (DICOM) para o uvicorn/FastAPI
    // falhava com HTTP/2 ("fixed content-length ... bytes received: 0").
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Achado sugerido pela IA. severidade: INFORMATIVO | ATENCAO | CRITICO. */
    public record ResultadoMonai(String achado, String severidade, BigDecimal score,
                                 String modelo, String versaoModelo, String detalhesJson, String erro) {
        public boolean ok() { return erro == null; }
        public static ResultadoMonai erro(String msg) {
            return new ResultadoMonai(null, "INFORMATIVO", null, null, null, null, msg);
        }
    }

    public boolean configurado() {
        return url.map(v -> v != null && !v.isBlank()).orElse(false);
    }

    /** Envia um estudo DICOM ao servico MONAI e devolve o achado sugerido. */
    public ResultadoMonai analisar(byte[] dicom, String descricaoExame) {
        return analisar(dicom, descricaoExame, null);
    }

    /** baseOverride: URL do MONAI da unidade (config no banco); vazio = usa a property. */
    public ResultadoMonai analisar(byte[] dicom, String descricaoExame, String baseOverride) {
        String baseUrl = (baseOverride != null && !baseOverride.isBlank())
                ? baseOverride
                : url.orElse("");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResultadoMonai.erro("Servico MONAI nao configurado.");
        }
        if (dicom == null || dicom.length == 0) {
            return ResultadoMonai.erro("Imagem DICOM vazia.");
        }
        return enviar(baseUrl, "/analisar", "application/octet-stream", dicom, descricaoExame, timeoutSeconds);
    }

    /**
     * Envia uma SERIE de TC (ZIP de DICOMs do Orthanc) ao servico. Mais lento que
     * o raio-X (volume inteiro em CPU): roda em segundo plano no ImagemIaService.
     */
    public ResultadoMonai analisarSerie(byte[] zip, String descricaoExame, String baseOverride) {
        String baseUrl = (baseOverride != null && !baseOverride.isBlank())
                ? baseOverride
                : url.orElse("");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResultadoMonai.erro("Servico MONAI nao configurado.");
        }
        if (zip == null || zip.length == 0) {
            return ResultadoMonai.erro("Serie de TC vazia.");
        }
        return enviar(baseUrl, "/analisar-serie", "application/zip", zip, descricaoExame, timeoutTcSeconds);
    }

    private ResultadoMonai enviar(String baseUrl, String caminho, String contentType, byte[] corpo,
                                  String descricaoExame, long timeout) {
        try {
            String base = baseUrl.replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + caminho))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", contentType)
                    .header("X-Exame", descricaoExame == null ? "" : descricaoExame.replaceAll("[\\r\\n]+", " "))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return ResultadoMonai.erro("MONAI HTTP " + response.statusCode() + erroDoCorpo(response.body()));
            }
            return parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.errorf("Falha ao chamar servico MONAI: %s", e.getMessage());
            return ResultadoMonai.erro(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ResultadoMonai parse(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            String achado = texto(n, "achado");
            String severidade = normalizarSeveridade(texto(n, "severidade"));
            BigDecimal score = n.hasNonNull("score") ? n.get("score").decimalValue() : null;
            String modelo = texto(n, "modelo");
            String versao = texto(n, "versao_modelo");
            return new ResultadoMonai(achado, severidade, score, modelo, versao, json, null);
        } catch (Exception e) {
            return ResultadoMonai.erro("Resposta invalida do MONAI.");
        }
    }

    /** GET /health do servico; devolve o JSON (status + modelos ligados). Lanca se fora do ar. */
    public JsonNode health(String baseUrl) throws Exception {
        String base = baseUrl.replaceAll("/+$", "");
        HttpResponse<String> r = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + r.statusCode());
        }
        return objectMapper.readTree(r.body());
    }

    /** Mensagem "erro" do JSON de falha do servico (ex.: serie nao axial), se houver. */
    private String erroDoCorpo(byte[] body) {
        try {
            String e = texto(objectMapper.readTree(body), "erro");
            return e == null ? "" : ": " + e;
        } catch (Exception ignore) {
            return "";
        }
    }

    private static String texto(JsonNode n, String campo) {
        return n != null && n.hasNonNull(campo) ? n.get(campo).asText() : null;
    }

    private static String normalizarSeveridade(String s) {
        if (s == null) return "INFORMATIVO";
        String up = s.trim().toUpperCase();
        return switch (up) {
            case "CRITICO", "CRITICAL" -> "CRITICO";
            case "ATENCAO", "WARNING", "ATTENTION" -> "ATENCAO";
            default -> "INFORMATIVO";
        };
    }
}
