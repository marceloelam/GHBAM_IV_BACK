package br.com.iv.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.Servidor;
import br.com.iv.repository.ServidorRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

/**
 * Identificacao facial 1:N (tela /biofoto/webcam) usando o servico Recognition do
 * CompreFace da VPS. O reset de senha continua no servico de verificacao 1:1
 * (ReconhecimentoFacialService); aqui a chave e o servico sao outros.
 *
 * Cada servidor ativo com foto vira um subject "matricula_hash", onde hash e um
 * resumo da foto. Assim a sincronizacao percebe sozinha foto trocada ou servidor
 * inativado, sem mexer no fluxo de gravacao do cadastro. Na identificacao, o
 * candidato do CompreFace so e aceito se continua ativo e com a mesma foto.
 */
@ApplicationScoped
public class IdentificacaoFacialService {

    private static final Logger LOGGER = Logger.getLogger(IdentificacaoFacialService.class.getName());
    private static final double LIMIAR_MINIMO_SEGURO = 0.85;
    private static final int TAMANHO_HASH = 12;

    @ConfigProperty(name = "ghbam.face.recognition.url")
    Optional<String> recognitionUrl;

    @ConfigProperty(name = "ghbam.face.recognition.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "ghbam.face.recognition.similarity-threshold", defaultValue = "0.9")
    double similarityThreshold;

    @ConfigProperty(name = "ghbam.face.timeout.seconds", defaultValue = "20")
    long timeoutSeconds;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ServidorRepository servidorRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final AtomicBoolean sincronizando = new AtomicBoolean(false);
    private volatile StatusSincronizacao status = StatusSincronizacao.inicial();

    public boolean configurado() {
        return recognitionUrl.map(this::valorConfigurado).orElse(false)
            && apiKey.map(this::valorConfigurado).orElse(false);
    }

    public StatusSincronizacao getStatus() {
        return status.comAndamento(sincronizando.get(), configurado());
    }

    // ------------------------------------------------------------------ identificacao

    public ResultadoIdentificacao identificar(String imagemBase64) {
        if (!configurado()) {
            return ResultadoIdentificacao.falha("Identificacao facial nao configurada no servidor.");
        }
        String imagem = normalizarBase64(imagemBase64);
        if (imagem.isEmpty()) {
            return ResultadoIdentificacao.falha("Imagem nao informada.");
        }
        try {
            HttpResponse<String> response = enviar("POST",
                "/recognize?limit=1&prediction_count=1&det_prob_threshold=0.8",
                objectMapper.writeValueAsString(Map.of("file", imagem)));

            if (response.statusCode() == 400 && response.body() != null && response.body().contains("No face")) {
                return ResultadoIdentificacao.semRosto();
            }
            if (!sucesso(response)) {
                LOGGER.warning("[IdentificacaoFacial] recognize HTTP " + response.statusCode() + ": " + limitar(response.body()));
                return ResultadoIdentificacao.falha(mensagemPorStatus(response.statusCode()));
            }

            JsonNode melhor = null;
            for (JsonNode rosto : objectMapper.readTree(response.body()).path("result")) {
                for (JsonNode candidato : rosto.path("subjects")) {
                    if (melhor == null || candidato.path("similarity").asDouble(0) > melhor.path("similarity").asDouble(0)) {
                        melhor = candidato;
                    }
                }
            }
            if (melhor == null) {
                return ResultadoIdentificacao.naoReconhecido(0);
            }

            double similaridade = melhor.path("similarity").asDouble(0);
            if (similaridade < limiarSeguro()) {
                return ResultadoIdentificacao.naoReconhecido(similaridade);
            }

            String subject = melhor.path("subject").asText("");
            String matricula = matriculaDoSubject(subject);
            Servidor servidor = matricula == null ? null : servidorRepository.localizarMatricula(matricula);
            if (servidor == null || !Boolean.TRUE.equals(servidor.getAtivo())
                || !subject.equals(nomeSubject(servidor.getMatricula(), servidor.getFoto()))) {
                // Cadastro mudou depois da ultima sincronizacao: nao aceita e agenda atualizacao.
                LOGGER.info("[IdentificacaoFacial] subject desatualizado; agendando sincronizacao");
                iniciarSincronizacao();
                return ResultadoIdentificacao.naoReconhecido(similaridade);
            }
            return ResultadoIdentificacao.sucesso(servidor, similaridade);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[IdentificacaoFacial] Erro ao identificar", e);
            return ResultadoIdentificacao.falha("Servico de reconhecimento facial indisponivel.");
        }
    }

    // ------------------------------------------------------------------ sincronizacao

    // Laco longo de chamadas HTTP ao CompreFace: thread virtual nao prende worker do Vert.x.
    @Scheduled(every = "6h", delayed = "2m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @RunOnVirtualThread
    void sincronizacaoAgendada() {
        if (!configurado() || !sincronizando.compareAndSet(false, true)) {
            return;
        }
        try {
            executarSincronizacao(carregarAlvos());
        } finally {
            sincronizando.set(false);
        }
    }

    /**
     * Le os servidores no contexto da requisicao e envia ao CompreFace em segundo plano.
     * Devolve false se nao configurado ou se ja houver uma sincronizacao em andamento.
     */
    public boolean iniciarSincronizacao() {
        if (!configurado() || !sincronizando.compareAndSet(false, true)) {
            return false;
        }
        Map<String, String> alvos;
        try {
            alvos = carregarAlvos();
        } catch (RuntimeException e) {
            sincronizando.set(false);
            throw e;
        }
        CompletableFuture.runAsync(() -> {
            try {
                executarSincronizacao(alvos);
            } finally {
                sincronizando.set(false);
            }
        });
        return true;
    }

    /** subject esperado -> foto (base64 normalizado) de cada servidor ativo com foto. */
    private Map<String, String> carregarAlvos() {
        Map<String, String> alvos = new HashMap<>();
        for (Servidor s : servidorRepository.listAll()) {
            if (!Boolean.TRUE.equals(s.getAtivo()) || s.getMatricula() == null || s.getMatricula().isBlank()) {
                continue;
            }
            String foto = normalizarBase64(s.getFoto());
            if (!foto.isEmpty()) {
                alvos.put(nomeSubject(s.getMatricula(), s.getFoto()), foto);
            }
        }
        return alvos;
    }

    private void executarSincronizacao(Map<String, String> alvos) {
        int enviados = 0;
        int removidos = 0;
        int falhas = 0;
        String erro = null;
        try {
            Set<String> existentes = listarSubjects();

            for (String subject : existentes) {
                if (!alvos.containsKey(subject)) {
                    HttpResponse<String> r = enviar("DELETE", "/subjects/" + codificar(subject), null);
                    if (sucesso(r)) removidos++; else falhas++;
                }
            }

            List<String> faltantes = new ArrayList<>();
            for (String subject : alvos.keySet()) {
                if (!existentes.contains(subject)) faltantes.add(subject);
            }
            for (String subject : faltantes) {
                HttpResponse<String> r = enviar("POST",
                    "/faces?subject=" + codificar(subject) + "&det_prob_threshold=0.8",
                    objectMapper.writeValueAsString(Map.of("file", alvos.get(subject))));
                if (sucesso(r)) {
                    enviados++;
                } else {
                    // Foto sem rosto detectavel e o caso comum; nao interrompe as demais.
                    falhas++;
                    LOGGER.fine("[IdentificacaoFacial] falha ao cadastrar subject: HTTP " + r.statusCode());
                }
            }
        } catch (Exception e) {
            erro = "Falha ao sincronizar com o CompreFace.";
            LOGGER.log(Level.WARNING, "[IdentificacaoFacial] Erro na sincronizacao", e);
        }
        status = new StatusSincronizacao(LocalDateTime.now().toString(), alvos.size(), enviados, removidos, falhas,
            erro, false, true);
        LOGGER.info("[IdentificacaoFacial] sincronizacao: alvos=" + alvos.size() + " enviados=" + enviados
            + " removidos=" + removidos + " falhas=" + falhas);
    }

    private Set<String> listarSubjects() throws Exception {
        HttpResponse<String> response = enviar("GET", "/subjects", null);
        if (!sucesso(response)) {
            throw new IllegalStateException("subjects HTTP " + response.statusCode() + ": " + limitar(response.body()));
        }
        Set<String> subjects = new HashSet<>();
        for (JsonNode s : objectMapper.readTree(response.body()).path("subjects")) {
            subjects.add(s.asText());
        }
        return subjects;
    }

    // ------------------------------------------------------------------ utilitarios

    static String nomeSubject(String matricula, String foto) {
        return matricula.trim() + "_" + resumo(normalizarBase64(foto));
    }

    static String matriculaDoSubject(String subject) {
        int separador = subject == null ? -1 : subject.lastIndexOf('_');
        return separador > 0 ? subject.substring(0, separador) : null;
    }

    private static String resumo(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, TAMANHO_HASH);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalizarBase64(String valor) {
        if (valor == null) return "";
        String normalizado = valor.trim();
        int virgula = normalizado.indexOf(',');
        if (virgula >= 0 && virgula < normalizado.length() - 1) {
            normalizado = normalizado.substring(virgula + 1);
        }
        return normalizado.replaceAll("\\s+", "");
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String corpo) throws Exception {
        String base = recognitionUrl.get().trim().replaceAll("/+$", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(base + caminho))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("x-api-key", apiKey.get().trim());
        if (corpo != null) {
            builder.header("Content-Type", MediaType.APPLICATION_JSON)
                .method(metodo, HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        } else {
            builder.method(metodo, HttpRequest.BodyPublishers.noBody());
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean sucesso(HttpResponse<String> r) {
        return r.statusCode() >= 200 && r.statusCode() < 300;
    }

    private double limiarSeguro() {
        return Math.max(similarityThreshold, LIMIAR_MINIMO_SEGURO);
    }

    private String mensagemPorStatus(int status) {
        if (status == 401 || status == 403) {
            return "Chave do servico de identificacao facial invalida.";
        }
        return "Servico de reconhecimento facial indisponivel.";
    }

    private String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean valorConfigurado(String valor) {
        if (valor == null || valor.isBlank()) return false;
        String normalizado = valor.trim().toUpperCase();
        return !normalizado.startsWith("COLE_") && !normalizado.contains("TROQUE_") && !normalizado.contains("AQUI");
    }

    private String limitar(String valor) {
        if (valor == null) return "";
        return valor.length() > 300 ? valor.substring(0, 300) + "..." : valor;
    }

    // ------------------------------------------------------------------ tipos

    public record ResultadoIdentificacao(boolean identificado, boolean rostoDetectado, double similaridade,
                                         Servidor servidor, String erro) {
        static ResultadoIdentificacao sucesso(Servidor servidor, double similaridade) {
            return new ResultadoIdentificacao(true, true, similaridade, servidor, null);
        }
        static ResultadoIdentificacao naoReconhecido(double similaridade) {
            return new ResultadoIdentificacao(false, true, similaridade, null, null);
        }
        static ResultadoIdentificacao semRosto() {
            return new ResultadoIdentificacao(false, false, 0, null, null);
        }
        static ResultadoIdentificacao falha(String erro) {
            return new ResultadoIdentificacao(false, false, 0, null, erro);
        }
    }

    public record StatusSincronizacao(String ultimaSincronizacao, int servidoresComFoto, int enviados, int removidos,
                                      int falhas, String erro, boolean emAndamento, boolean configurado) {
        static StatusSincronizacao inicial() {
            return new StatusSincronizacao(null, 0, 0, 0, 0, null, false, false);
        }
        StatusSincronizacao comAndamento(boolean andamento, boolean config) {
            return new StatusSincronizacao(ultimaSincronizacao, servidoresComFoto, enviados, removidos, falhas, erro,
                andamento, config);
        }
    }
}
