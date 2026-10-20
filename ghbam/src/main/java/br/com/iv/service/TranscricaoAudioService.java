package br.com.iv.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.logging.Logger;

import br.gov.secure.AppSecrets;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TranscricaoAudioService {

    private static final Logger LOG = Logger.getLogger(TranscricaoAudioService.class);
    private static final long MAX_AUDIO_BYTES = 25L * 1024L * 1024L;
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final String OPENAI_TRANSCRIPTIONS_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String GROQ_TRANSCRIPTIONS_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini-transcribe";
    private static final String OPENAI_DIARIZATION_MODEL = "gpt-4o-transcribe-diarize";
    private static final String DEFAULT_GROQ_MODEL = "whisper-large-v3";
    // Glossario do Whisper (campo "prompt"): ensina so a GRAFIA de termos. NUNCA usar frases
    // clinicas de exemplo: o Whisper copia trechos do prompt para a transcricao quando o audio
    // e curto/baixo - um exemplo com "hipertenso e diabetico" virou achado inventado no ditado.
    // Por isso aqui so ha medicamentos, unidades e siglas, e ecoDoVocabulario() barra copia.
    // Sobrescreva com ghbam.ditado.vocabulario; "off" desliga o glossario.
    private static final String VOCABULARIO_PADRAO = "Glossário: dipirona, paracetamol, ibuprofeno, cetoprofeno, "
            + "tramadol, morfina, ondansetrona, metoclopramida, bromoprida, omeprazol, pantoprazol, escopolamina, "
            + "ceftriaxona, amoxicilina, clavulanato, azitromicina, ciprofloxacino, cefalexina, dexametasona, "
            + "hidrocortisona, prednisona, salbutamol, ipratrópio, losartana, captopril, enalapril, anlodipino, "
            + "hidroclorotiazida, furosemida, metformina, insulina NPH, glibenclamida, enoxaparina, heparina, "
            + "diazepam, haloperidol, prometazina; mg, mcg, g, mL, UI, gotas, EV, IM, VO, SC, SL, "
            + "6/6 h, 8/8 h, 12/12 h, SOS, ACM, PA, FC, FR, SatO2, HGT, mmHg, bpm.";

    @ConfigProperty(name = "ghbam.ditado.vocabulario")
    Optional<String> vocabularioConfigurado;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String transcrever(org.jboss.resteasy.reactive.multipart.FileUpload audio) throws Exception {
        return transcrever(audio, resolverProvedor(), false);
    }

    private String transcrever(org.jboss.resteasy.reactive.multipart.FileUpload audio, ProvedorTranscricao provedor, boolean identificarInterlocutores) throws Exception {
        if (audio == null || audio.uploadedFile() == null) {
            throw new IllegalArgumentException("Audio nao enviado.");
        }

        long tamanho = Files.size(audio.uploadedFile());
        if (tamanho <= 0) {
            throw new IllegalArgumentException("Audio vazio.");
        }
        if (tamanho > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("Audio maior que 25 MB.");
        }

        byte[] bytes = Files.readAllBytes(audio.uploadedFile());
        String fileName = audio.fileName() == null || audio.fileName().isBlank() ? "ditado.webm" : audio.fileName();
        String contentType = audio.contentType() == null || audio.contentType().isBlank() ? "audio/webm" : audio.contentType();

        String boundary = "----GHBAMTranscricao" + UUID.randomUUID();
        byte[] body = montarMultipart(boundary, provedor.model(), fileName, contentType, bytes, identificarInterlocutores);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(provedor.url()))
                .header("Authorization", "Bearer " + provedor.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Servico de transcricao retornou status " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String texto = json.path("text").asText("").trim();

        // Diagnostico de fidelidade sem expor fala de paciente: so os numeros. Audio pequeno
        // demais para o tempo falado indica captacao falhando; audio normal com texto curto
        // indica o modelo perdendo fala.
        LOG.infof("Ditado: provedor=%s modelo=%s diarizacao=%s audio=%d bytes (%s) texto=%d chars",
                provedor.url().contains("groq") ? "groq" : "openai",
                provedor.model(), identificarInterlocutores, tamanho, contentType, texto.length());
        if (texto.isBlank()) {
            throw new RuntimeException("Servico de transcricao nao retornou texto.");
        }
        if (transcricaoInvalida(texto)) {
            throw new RuntimeException("Servico de transcricao retornou texto invalido.");
        }
        return texto;
    }

    public ResultadoConversaClinica gerarAnamneseDaConversa(org.jboss.resteasy.reactive.multipart.FileUpload audio) throws Exception {
        String transcricao = transcrever(audio, true).trim();
        if (transcricaoInvalida(transcricao)) {
            throw new RuntimeException("A transcricao da conversa nao retornou texto.");
        }

        // Texto bruto, sem rotulo e sem etapa generativa: o que o medico le no prontuario
        // e exatamente o que o servico de transcricao devolveu.
        return new ResultadoConversaClinica(transcricao, transcricao);
    }

    private String transcrever(org.jboss.resteasy.reactive.multipart.FileUpload audio, boolean identificarInterlocutores) throws Exception {
        if (!identificarInterlocutores) {
            return transcrever(audio);
        }

        ProvedorTranscricao provedorPadrao = resolverProvedor();
        if (!provedorPadrao.url().contains("api.openai.com")) {
            return transcrever(audio);
        }

        return transcrever(audio, new ProvedorTranscricao(
                provedorPadrao.url(), provedorPadrao.apiKey(), OPENAI_DIARIZATION_MODEL), true);
    }

    private ProvedorTranscricao resolverProvedor() {
        if (!AppSecrets.TRANSCRIPTION_API_KEY.isBlank()) {
            String url = AppSecrets.TRANSCRIPTION_API_URL.isBlank() ? OPENAI_TRANSCRIPTIONS_URL : AppSecrets.TRANSCRIPTION_API_URL;
            String model = AppSecrets.TRANSCRIPTION_MODEL.isBlank() ? DEFAULT_OPENAI_MODEL : AppSecrets.TRANSCRIPTION_MODEL;
            return new ProvedorTranscricao(url, AppSecrets.TRANSCRIPTION_API_KEY, model);
        }
        if (!AppSecrets.OPENAI_API_KEY.isBlank()) {
            String url = AppSecrets.TRANSCRIPTION_API_URL.isBlank() ? OPENAI_TRANSCRIPTIONS_URL : AppSecrets.TRANSCRIPTION_API_URL;
            String model = AppSecrets.TRANSCRIPTION_MODEL.isBlank() ? DEFAULT_OPENAI_MODEL : AppSecrets.TRANSCRIPTION_MODEL;
            return new ProvedorTranscricao(url, AppSecrets.OPENAI_API_KEY, model);
        }
        if (!AppSecrets.GROQ_API_KEY.isBlank()) {
            String url = AppSecrets.TRANSCRIPTION_API_URL.isBlank() ? GROQ_TRANSCRIPTIONS_URL : AppSecrets.TRANSCRIPTION_API_URL;
            String model = AppSecrets.TRANSCRIPTION_MODEL.isBlank() ? DEFAULT_GROQ_MODEL : AppSecrets.TRANSCRIPTION_MODEL;
            return new ProvedorTranscricao(url, AppSecrets.GROQ_API_KEY, model);
        }
        throw new IllegalStateException("Configure TRANSCRIPTION_API_KEY, OPENAI_API_KEY ou GROQ_API_KEY para usar transcricao de audio.");
    }

    private byte[] montarMultipart(String boundary, String model, String fileName, String contentType, byte[] fileBytes, boolean identificarInterlocutores) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        escrever(output, "--" + boundary + "\r\n");
        escrever(output, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        escrever(output, model + "\r\n");
        escrever(output, "--" + boundary + "\r\n");
        escrever(output, "Content-Disposition: form-data; name=\"language\"\r\n\r\n");
        escrever(output, "pt\r\n");
        escrever(output, "--" + boundary + "\r\n");
        escrever(output, "Content-Disposition: form-data; name=\"temperature\"\r\n\r\n");
        escrever(output, "0\r\n");
        escrever(output, "--" + boundary + "\r\n");
        if (!identificarInterlocutores && !vocabulario().isBlank()) {
            // O modelo de diarizacao da OpenAI nao aceita prompt.
            escrever(output, "Content-Disposition: form-data; name=\"prompt\"\r\n\r\n");
            escrever(output, vocabulario() + "\r\n");
            escrever(output, "--" + boundary + "\r\n");
        }
        if (identificarInterlocutores) {
            escrever(output, "Content-Disposition: form-data; name=\"chunking_strategy\"\r\n\r\n");
            escrever(output, "auto\r\n");
            escrever(output, "--" + boundary + "\r\n");
        }
        escrever(output, "Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        escrever(output, identificarInterlocutores ? "diarized_json\r\n" : "json\r\n");
        escrever(output, "--" + boundary + "\r\n");
        escrever(output, "Content-Disposition: form-data; name=\"file\"; filename=\"" + sanitizarNomeArquivo(fileName) + "\"\r\n");
        escrever(output, "Content-Type: " + contentType + "\r\n\r\n");
        output.write(fileBytes);
        escrever(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void escrever(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitizarNomeArquivo(String fileName) {
        return fileName.replace("\\", "_").replace("/", "_").replace("\"", "_");
    }

    private boolean transcricaoInvalida(String texto) {
        if (texto == null || texto.isBlank()) {
            return true;
        }

        // So o texto que o Whisper notoriamente inventa em silencio e barrado. A regra
        // antiga tambem descartava transcricao curta sem termo clinico, e isso engolia
        // fala legitima ("bom dia, estou com dor") - quem julga o conteudo e o medico.
        String normalizado = texto.toLowerCase();
        return normalizado.contains("aprenda a falar com")
                || normalizado.contains("dr. marco paret")
                || normalizado.contains("marco paret")
                || normalizado.contains("foi rece")
                || normalizado.contains("legendas pela comunidade")
                || normalizado.contains("amara.org")
                // Em audio silencioso o Whisper pode devolver o proprio vocabulario.
                || normalizado.contains("glossário")
                || ecoDoVocabulario(normalizado);
    }

    private String vocabulario() {
        return vocabularioConfigurado
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .map(v -> "off".equalsIgnoreCase(v) ? "" : v)
                .orElse(VOCABULARIO_PADRAO);
    }

    private static final int EXTENSAO_ECO = 5;

    // Barra a transcricao que contem 5+ palavras seguidas do glossario, na mesma ordem:
    // fala real nao repete a lista; copia do prompt repete.
    private boolean ecoDoVocabulario(String normalizado) {
        String[] glossario = palavras(vocabulario());
        if (glossario.length < EXTENSAO_ECO) {
            return false;
        }
        String texto = " " + String.join(" ", palavras(normalizado)) + " ";
        for (int i = 0; i + EXTENSAO_ECO <= glossario.length; i++) {
            String trecho = String.join(" ", java.util.Arrays.copyOfRange(glossario, i, i + EXTENSAO_ECO));
            if (texto.contains(" " + trecho + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String[] palavras(String texto) {
        String limpo = texto.toLowerCase().replaceAll("[^\\p{L}\\p{N}/]+", " ").trim();
        return limpo.isEmpty() ? new String[0] : limpo.split(" ");
    }

    private record ProvedorTranscricao(String url, String apiKey, String model) {
    }

    public record ResultadoConversaClinica(String anamnese, String transcricao) {
    }
}
