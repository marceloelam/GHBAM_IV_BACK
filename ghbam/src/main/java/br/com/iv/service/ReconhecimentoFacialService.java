package br.com.iv.service;

import java.net.URI;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
public class ReconhecimentoFacialService {

    private static final Logger LOGGER = Logger.getLogger(ReconhecimentoFacialService.class.getName());
    private static final double MINIMUM_SAFE_SIMILARITY_THRESHOLD = 0.85;

    @ConfigProperty(name = "ghbam.face.verification.url")
    Optional<String> verificationUrl;

    @ConfigProperty(name = "ghbam.face.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "ghbam.face.similarity-threshold", defaultValue = "0.85")
    double similarityThreshold;

    @ConfigProperty(name = "ghbam.face.timeout.seconds", defaultValue = "20")
    long timeoutSeconds;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public boolean configurado() {
        return verificationUrl.map(this::valorConfigurado).orElse(false)
            && apiKey.map(this::valorConfigurado).orElse(false);
    }

    public double getSimilarityThreshold() {
        return thresholdSeguro();
    }

    public ResultadoValidacao validar(String fotoCadastradaBase64, String fotoCapturadaBase64) {
        if (!configurado()) {
            return ResultadoValidacao.falha("Servico de reconhecimento facial nao configurado.");
        }

        if (fotoCadastradaBase64 == null || fotoCadastradaBase64.isBlank()
            || fotoCapturadaBase64 == null || fotoCapturadaBase64.isBlank()) {
            return ResultadoValidacao.falha("Foto cadastrada e foto capturada sao obrigatorias.");
        }

        try {
            String fotoCadastradaNormalizada = normalizarBase64(fotoCadastradaBase64);
            String fotoCapturadaNormalizada = normalizarBase64(fotoCapturadaBase64);
            validarImagemBase64(fotoCadastradaNormalizada, "foto cadastrada");
            validarImagemBase64(fotoCapturadaNormalizada, "foto capturada");

            Map<String, String> payload = new HashMap<>();
            payload.put("source_image", fotoCadastradaNormalizada);
            payload.put("target_image", fotoCapturadaNormalizada);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(verificationUrl.get()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("x-api-key", apiKey.get())
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warning("[ReconhecimentoFacial] CompreFace HTTP " + response.statusCode() + ": "
                    + limitar(response.body(), 500));
                return ResultadoValidacao.falha(mensagemPorStatus(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            double similaridade = normalizarSimilaridade(maiorSimilaridade(root));
            double threshold = thresholdSeguro();
            return similaridade >= threshold
                ? ResultadoValidacao.sucesso(similaridade)
                : ResultadoValidacao.falha(
                    similaridade,
                    "Rosto nao reconhecido para esta matricula. Similaridade "
                        + percentual(similaridade) + "%; minimo exigido " + percentual(threshold) + "%."
                );
        } catch (ConnectException e) {
            LOGGER.log(Level.WARNING, "[ReconhecimentoFacial] Servico facial indisponivel", e);
            return ResultadoValidacao.falha("Servico de reconhecimento facial indisponivel. Verifique se o CompreFace esta em execucao e acessivel pelo backend.");
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "[ReconhecimentoFacial] Imagem em Base64 invalida", e);
            return ResultadoValidacao.falha(e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ReconhecimentoFacial] Erro ao consultar servico facial", e);
            return ResultadoValidacao.falha("Erro ao consultar reconhecimento facial.");
        }
    }

    private boolean valorConfigurado(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        String normalizado = valor.trim().toUpperCase();
        return !normalizado.startsWith("COLE_")
            && !normalizado.contains("TROQUE_")
            && !normalizado.contains("AQUI");
    }

    private String normalizarBase64(String valor) {
        String normalizado = valor.trim();
        int virgula = normalizado.indexOf(',');
        if (virgula >= 0 && virgula < normalizado.length() - 1) {
            normalizado = normalizado.substring(virgula + 1);
        }
        return normalizado.replaceAll("\\s+", "");
    }

    private void validarImagemBase64(String valor, String origem) {
        try {
            Base64.getDecoder().decode(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Imagem em Base64 invalida na " + origem + ".", e);
        }
    }

    private String mensagemPorStatus(int status, String corpo) {
        String detalhe = detalheResposta(corpo);
        return switch (status) {
            case 400 -> detalhe == null || detalhe.isBlank()
                ? "Reconhecimento facial recusou a imagem. Verifique se as duas fotos possuem rosto visivel."
                : "Reconhecimento facial recusou a imagem: " + detalhe;
            case 401, 403 -> "Chave do reconhecimento facial invalida ou sem permissao.";
            case 404 -> "Endpoint do reconhecimento facial nao encontrado.";
            case 429 -> "Reconhecimento facial recebeu muitas tentativas. Aguarde e tente novamente.";
            default -> "Servico de reconhecimento facial recusou a validacao.";
        };
    }

    private String detalheResposta(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(corpo);
            String mensagem = primeiroTexto(root, "message", "mensagem", "error", "detail", "details");
            return mensagem == null || mensagem.isBlank() ? limitar(corpo, 180) : limitar(mensagem, 180);
        } catch (Exception e) {
            return limitar(corpo, 180);
        }
    }

    private String primeiroTexto(JsonNode node, String... nomes) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            for (String nome : nomes) {
                JsonNode valor = node.get(nome);
                if (valor != null && valor.isTextual()) {
                    return valor.asText();
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String texto = primeiroTexto(fields.next().getValue(), nomes);
                if (texto != null && !texto.isBlank()) {
                    return texto;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String texto = primeiroTexto(child, nomes);
                if (texto != null && !texto.isBlank()) {
                    return texto;
                }
            }
        } else if (node.isTextual()) {
            return node.asText();
        }
        return null;
    }

    private String limitar(String valor, int maximo) {
        if (valor == null) {
            return "";
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private double maiorSimilaridade(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        double melhor = 0;
        if (node.isObject()) {
            JsonNode similarity = node.get("similarity");
            if (similarity != null && similarity.isNumber()) {
                melhor = Math.max(melhor, similarity.asDouble());
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                melhor = Math.max(melhor, maiorSimilaridade(fields.next().getValue()));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                melhor = Math.max(melhor, maiorSimilaridade(child));
            }
        }
        return melhor;
    }

    private double normalizarSimilaridade(double valor) {
        if (!Double.isFinite(valor) || valor <= 0) {
            return 0;
        }
        if (valor > 1 && valor <= 100) {
            return valor / 100;
        }
        return valor > 1 ? 0 : valor;
    }

    private double thresholdSeguro() {
        if (!Double.isFinite(similarityThreshold)) {
            return MINIMUM_SAFE_SIMILARITY_THRESHOLD;
        }
        double normalizado = normalizarSimilaridade(similarityThreshold);
        return Math.max(normalizado, MINIMUM_SAFE_SIMILARITY_THRESHOLD);
    }

    private int percentual(double valor) {
        return (int) Math.round(valor * 100);
    }

    public static class ResultadoValidacao {
        private final boolean aprovado;
        private final double similaridade;
        private final String mensagem;

        private ResultadoValidacao(boolean aprovado, double similaridade, String mensagem) {
            this.aprovado = aprovado;
            this.similaridade = similaridade;
            this.mensagem = mensagem;
        }

        public static ResultadoValidacao sucesso(double similaridade) {
            return new ResultadoValidacao(true, similaridade, "Reconhecimento facial aprovado.");
        }

        public static ResultadoValidacao falha(String mensagem) {
            return new ResultadoValidacao(false, 0, mensagem);
        }

        public static ResultadoValidacao falha(double similaridade, String mensagem) {
            return new ResultadoValidacao(false, similaridade, mensagem);
        }

        public boolean isAprovado() {
            return aprovado;
        }

        public double getSimilaridade() {
            return similaridade;
        }

        public String getMensagem() {
            return mensagem;
        }
    }

}
