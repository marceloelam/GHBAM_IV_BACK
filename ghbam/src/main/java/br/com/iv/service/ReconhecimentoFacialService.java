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
        Consulta consulta = consultar(fotoCadastradaBase64, fotoCapturadaBase64, null);
        if (consulta.falha() != null) {
            return ResultadoValidacao.falha(consulta.falha());
        }

        double similaridade = normalizarSimilaridade(maiorSimilaridade(consulta.root()));
        double threshold = thresholdSeguro();
        return similaridade >= threshold
            ? ResultadoValidacao.sucesso(similaridade)
            : ResultadoValidacao.falha(
                similaridade,
                "Rosto nao reconhecido para esta matricula. Similaridade "
                    + percentual(similaridade) + "%; minimo exigido " + percentual(threshold) + "%."
            );
    }

    /**
     * Compara a imagem alvo com a de referencia e devolve tambem a pose (yaw/pitch)
     * do rosto alvo, usada pela prova de vida. Nao aplica o limiar: quem chama decide.
     */
    public ResultadoPose compararComPose(String referenciaBase64, String alvoBase64) {
        return compararComPose(referenciaBase64, alvoBase64, null);
    }

    /**
     * @param limiarDeteccao confianca minima para o CompreFace aceitar um rosto
     *        (det_prob_threshold); null usa o padrao do servico. Rosto virado com
     *        pouca luz costuma precisar de um limiar menor.
     */
    public ResultadoPose compararComPose(String referenciaBase64, String alvoBase64, Double limiarDeteccao) {
        String parametros = "face_plugins=pose"
            + (limiarDeteccao == null ? "" : "&det_prob_threshold=" + limiarDeteccao);
        Consulta consulta = consultar(referenciaBase64, alvoBase64, parametros);
        if (consulta.falha() != null) {
            return ResultadoPose.falha(consulta.falha());
        }

        JsonNode melhor = melhorCorrespondencia(consulta.root());
        if (melhor == null) {
            return ResultadoPose.falha("Rosto nao encontrado na imagem capturada.");
        }
        JsonNode yaw = melhor.path("pose").path("yaw");
        if (!yaw.isNumber()) {
            return ResultadoPose.falha("Servico facial nao retornou a pose do rosto. Verifique o plugin 'pose' do CompreFace.");
        }
        double similaridade = normalizarSimilaridade(melhor.path("similarity").asDouble(0));
        return ResultadoPose.sucesso(similaridade, yaw.asDouble(), melhor.path("pose").path("pitch").asDouble(0));
    }

    private Consulta consultar(String fotoReferenciaBase64, String fotoAlvoBase64, String parametros) {
        if (!configurado()) {
            return Consulta.erro("Servico de reconhecimento facial nao configurado.");
        }

        if (fotoReferenciaBase64 == null || fotoReferenciaBase64.isBlank()
            || fotoAlvoBase64 == null || fotoAlvoBase64.isBlank()) {
            return Consulta.erro("Foto cadastrada e foto capturada sao obrigatorias.");
        }

        try {
            String fotoReferenciaNormalizada = normalizarBase64(fotoReferenciaBase64);
            String fotoAlvoNormalizada = normalizarBase64(fotoAlvoBase64);
            validarImagemBase64(fotoReferenciaNormalizada, "foto cadastrada");
            validarImagemBase64(fotoAlvoNormalizada, "foto capturada");

            Map<String, String> payload = new HashMap<>();
            payload.put("source_image", fotoReferenciaNormalizada);
            payload.put("target_image", fotoAlvoNormalizada);

            String url = verificationUrl.get();
            if (parametros != null && !parametros.isBlank()) {
                url = url + (url.contains("?") ? "&" : "?") + parametros;
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("x-api-key", apiKey.get())
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warning("[ReconhecimentoFacial] CompreFace HTTP " + response.statusCode() + ": "
                    + limitar(response.body(), 500));
                return Consulta.erro(mensagemPorStatus(response.statusCode(), response.body()));
            }

            return new Consulta(objectMapper.readTree(response.body()), null);
        } catch (ConnectException e) {
            LOGGER.log(Level.WARNING, "[ReconhecimentoFacial] Servico facial indisponivel", e);
            return Consulta.erro("Servico de reconhecimento facial indisponivel. Verifique se o CompreFace esta em execucao e acessivel pelo backend.");
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "[ReconhecimentoFacial] Imagem em Base64 invalida", e);
            return Consulta.erro(e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ReconhecimentoFacial] Erro ao consultar servico facial", e);
            return Consulta.erro("Erro ao consultar reconhecimento facial.");
        }
    }

    private JsonNode melhorCorrespondencia(JsonNode root) {
        JsonNode melhor = null;
        for (JsonNode resultado : root.path("result")) {
            for (JsonNode match : resultado.path("face_matches")) {
                if (melhor == null || match.path("similarity").asDouble(0) > melhor.path("similarity").asDouble(0)) {
                    melhor = match;
                }
            }
        }
        return melhor;
    }

    private record Consulta(JsonNode root, String falha) {
        static Consulta erro(String mensagem) {
            return new Consulta(null, mensagem);
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

    public record ResultadoPose(boolean ok, double similaridade, double yaw, double pitch, String mensagem) {
        static ResultadoPose sucesso(double similaridade, double yaw, double pitch) {
            return new ResultadoPose(true, similaridade, yaw, pitch, null);
        }

        static ResultadoPose falha(String mensagem) {
            return new ResultadoPose(false, 0, 0, 0, mensagem);
        }
    }

}
