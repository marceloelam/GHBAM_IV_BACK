package br.com.iv.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.iv.iaauditoria.IaAuditoriaService;
import br.gov.secure.AppSecrets;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class AcessoIa {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String HF_ROUTER_URL = "https://router.huggingface.co/featherless-ai/v1/chat/completions";
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL_HF = "Qwen/Qwen2.5-7B-Instruct";
    private static final String DEFAULT_MODEL_OPENROUTER = "openrouter/free";
    private static final String DEFAULT_MODEL_GROQ = "llama-3.3-70b-versatile";
    private static final Duration TIMEOUT_GROQ = Duration.ofSeconds(15);
    private static final Duration TIMEOUT_HF = Duration.ofSeconds(12);
    private static final Duration TIMEOUT_OPENROUTER = Duration.ofSeconds(20);

    public AcessoIa() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ------------------------------------------------------------------ //
    //  Entradas publicas: toda chamada a LLM passa por auditado(...)       //
    // ------------------------------------------------------------------ //

    /** Provedor/modelo efetivamente usados na ultima tentativa (fallback Groq -> HF -> OpenRouter). */
    private static final ThreadLocal<String[]> DESTINO = new ThreadLocal<>();

    @Inject
    Instance<IaAuditoriaService> auditoria;

    @FunctionalInterface
    private interface ChamadaIa<T> {
        T executar() throws Exception;
    }

    public String enviarPergunta(String pergunta) throws Exception {
        return auditado(mensagemUsuario(pergunta), () -> enviarPerguntaSemAuditoria(pergunta));
    }

    public String enviarPergunta(String pergunta, String modelo) throws Exception {
        return auditado(mensagemUsuario(pergunta), () -> enviarPerguntaSemAuditoria(pergunta, modelo));
    }

    public String enviarMensagens(List<Map<String, String>> mensagens) throws Exception {
        return auditado(mensagens, () -> enviarMensagensSemAuditoria(mensagens));
    }

    public String enviarMensagens(List<Map<String, String>> mensagens, String modelo) throws Exception {
        return auditado(mensagens, () -> enviarMensagensSemAuditoria(mensagens, modelo));
    }

    public RespostaIa enviarMensagensComMetadados(List<Map<String, String>> mensagens, String modelo, Double temperatura, Integer maxTokens) throws Exception {
        return auditado(mensagens, () -> enviarMensagensComMetadadosSemAuditoria(mensagens, modelo, temperatura, maxTokens));
    }

    private <T> T auditado(List<Map<String, String>> mensagens, ChamadaIa<T> chamada) throws Exception {
        long inicio = System.nanoTime();
        DESTINO.remove();
        String chamador = classeChamadora();
        try {
            T resultado = chamada.executar();
            registrarAuditoria(mensagens, resultado, null, inicio, chamador);
            return resultado;
        } catch (Exception e) {
            registrarAuditoria(mensagens, null, e, inicio, chamador);
            throw e;
        } finally {
            DESTINO.remove();
        }
    }

    private void registrarAuditoria(List<Map<String, String>> mensagens, Object resultado, Throwable erro, long inicio, String chamador) {
        // Instanciado com "new AcessoIa()" (ferramentas de avaliacao de prompt): sem CDI, sem auditoria.
        if (auditoria == null || !auditoria.isResolvable()) {
            return;
        }
        String[] destino = DESTINO.get();
        String conteudo = null;
        Integer tokensEntrada = null;
        Integer tokensSaida = null;
        if (resultado instanceof RespostaIa r) {
            conteudo = r.conteudo();
            tokensEntrada = r.tokensPrompt();
            tokensSaida = r.tokensCompletion();
        } else if (resultado != null) {
            conteudo = resultado.toString();
        }
        auditoria.get().registrar(new IaAuditoriaService.Chamada(
                mensagens,
                destino != null ? destino[0] : null,
                destino != null ? destino[1] : null,
                conteudo, erro, inicio, tokensEntrada, tokensSaida, chamador));
    }

    private static String classeChamadora() {
        return StackWalker.getInstance().walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .filter(c -> !c.equals(AcessoIa.class.getName()) && !c.startsWith(AcessoIa.class.getName() + "$"))
                .map(c -> c.substring(c.lastIndexOf('.') + 1))
                .findFirst()
                .orElse(null));
    }

    private static List<Map<String, String>> mensagemUsuario(String pergunta) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", pergunta);
        return List.of(msg);
    }

    // ------------------------------------------------------------------ //
    //  Implementacao (sem auditoria)                                      //
    // ------------------------------------------------------------------ //

    private String enviarPerguntaSemAuditoria(String pergunta) throws Exception {
        if (AppSecrets.GROQ_API_KEY != null && !AppSecrets.GROQ_API_KEY.isBlank()) {
            try {
                return enviarPerguntaGroq(pergunta, DEFAULT_MODEL_GROQ);
            } catch (Exception e) {
                System.out.println("[AcessoIa] Groq falhou, tentando HF: " + e.getMessage());
            }
        }
        try {
            return enviarPerguntaHf(pergunta, AppSecrets.HF_MODEL != null && !AppSecrets.HF_MODEL.isBlank() ? AppSecrets.HF_MODEL : DEFAULT_MODEL_HF);
        } catch (Exception e) {
            System.out.println("[AcessoIa] HF falhou, tentando OpenRouter: " + e.getMessage());
        }
        if (AppSecrets.OPENROUTER_API_KEY != null && !AppSecrets.OPENROUTER_API_KEY.isBlank()) {
            return enviarPerguntaOpenRouter(pergunta, DEFAULT_MODEL_OPENROUTER);
        } else {
            throw new IllegalStateException("Nenhuma API key configurada (Groq, HF ou OpenRouter) em AppSecrets");
        }
    }

    private String enviarPerguntaSemAuditoria(String pergunta, String modelo) throws Exception {
        if (modelo.startsWith("hf:")) {
            return enviarPerguntaHf(pergunta, modelo.replace("hf:", ""));
        } else if (modelo.startsWith("groq:") || modelo.startsWith("llama") || modelo.startsWith("mistral")) {
            return enviarPerguntaGroq(pergunta, modelo.replace("groq:", ""));
        } else {
            return enviarPerguntaOpenRouter(pergunta, modelo);
        }
    }

    private String enviarPerguntaOpenRouter(String pergunta, String modelo) throws Exception {
        DESTINO.set(new String[] {"openrouter", modelo});
        if (AppSecrets.OPENROUTER_API_KEY == null || AppSecrets.OPENROUTER_API_KEY.isBlank()) {
            throw new IllegalStateException("OpenRouter API key nao configurada em AppSecrets");
        }

        String json = montarJsonSimples(modelo, pergunta);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENROUTER_API_URL))
                .header("Authorization", "Bearer " + AppSecrets.OPENROUTER_API_KEY)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("HTTP-Referer", "https://ghbam.hospitalit.com.br")
                .header("X-OpenRouter-Title", "GHBAM")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(TIMEOUT_OPENROUTER)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API OpenRouter: " + response.statusCode() + " - " + response.body());
        }
        return extrairResposta(response.body());
    }

    private String enviarPerguntaGroq(String pergunta, String modelo) throws Exception {
        DESTINO.set(new String[] {"groq", modelo});
        if (AppSecrets.GROQ_API_KEY == null || AppSecrets.GROQ_API_KEY.isBlank()) {
            throw new IllegalStateException("Groq API key nao configurada em AppSecrets");
        }

        String json = montarJsonSimples(modelo, pergunta);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + AppSecrets.GROQ_API_KEY)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(TIMEOUT_GROQ)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API Groq: " + response.statusCode() + " - " + response.body());
        }
        return extrairResposta(response.body());
    }

    private String enviarPerguntaHf(String pergunta, String modelo) throws Exception {
        DESTINO.set(new String[] {"huggingface", modelo});
        if (AppSecrets.HF_API_TOKEN == null || AppSecrets.HF_API_TOKEN.isBlank()) {
            throw new IllegalStateException("HF_API_TOKEN nao configurado em AppSecrets");
        }

        String json = montarJsonSimples(modelo, pergunta);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_ROUTER_URL))
                .header("Authorization", "Bearer " + AppSecrets.HF_API_TOKEN)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(TIMEOUT_HF)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro no HF Router: " + response.statusCode() + " - " + response.body());
        }
        return extrairResposta(response.body());
    }

    private String enviarMensagensSemAuditoria(List<Map<String, String>> mensagens) throws Exception {
        if (AppSecrets.GROQ_API_KEY != null && !AppSecrets.GROQ_API_KEY.isBlank()) {
            try {
                return enviarMensagensGroq(mensagens, DEFAULT_MODEL_GROQ);
            } catch (Exception e) {
                System.out.println("[AcessoIa] Groq falhou, tentando HF: " + e.getMessage());
            }
        }
        try {
            String model = AppSecrets.HF_MODEL != null && !AppSecrets.HF_MODEL.isBlank() ? AppSecrets.HF_MODEL : DEFAULT_MODEL_HF;
            return enviarMensagensHf(mensagens, model);
        } catch (Exception e) {
            System.out.println("[AcessoIa] HF falhou, tentando OpenRouter: " + e.getMessage());
        }
        if (AppSecrets.OPENROUTER_API_KEY != null && !AppSecrets.OPENROUTER_API_KEY.isBlank()) {
            return enviarMensagensOpenRouter(mensagens, DEFAULT_MODEL_OPENROUTER);
        } else {
            throw new IllegalStateException("Nenhuma API key configurada (Groq, HF ou OpenRouter) em AppSecrets");
        }
    }

    private String enviarMensagensSemAuditoria(List<Map<String, String>> mensagens, String modelo) throws Exception {
        if (modelo.startsWith("hf:")) {
            return enviarMensagensHf(mensagens, modelo.replace("hf:", ""));
        } else if (modelo.startsWith("groq:") || modelo.startsWith("llama") || modelo.startsWith("mistral")) {
            return enviarMensagensGroq(mensagens, modelo.replace("groq:", ""));
        } else {
            return enviarMensagensOpenRouter(mensagens, modelo);
        }
    }

    /**
     * Registro com o conteudo da resposta e metadados (provedor/tokens) para uso em
     * ferramentas de avaliacao/regressao de prompts. Nao usado pelo fluxo de producao.
     */
    public record RespostaIa(String conteudo, String provedor, Integer tokensPrompt, Integer tokensCompletion, Integer tokensTotal) {
    }

    private RespostaIa enviarMensagensComMetadadosSemAuditoria(List<Map<String, String>> mensagens, String modelo, Double temperatura, Integer maxTokens) throws Exception {
        String modeloEfetivo = modelo;
        if (modeloEfetivo == null || modeloEfetivo.isBlank()) {
            if (AppSecrets.GROQ_API_KEY != null && !AppSecrets.GROQ_API_KEY.isBlank()) {
                try {
                    return enviarMensagensGroqComMetadados(mensagens, DEFAULT_MODEL_GROQ, temperatura, maxTokens);
                } catch (Exception e) {
                    System.out.println("[AcessoIa] Groq falhou, tentando HF: " + e.getMessage());
                }
            }
            try {
                String model = AppSecrets.HF_MODEL != null && !AppSecrets.HF_MODEL.isBlank() ? AppSecrets.HF_MODEL : DEFAULT_MODEL_HF;
                return enviarMensagensHfComMetadados(mensagens, model, temperatura, maxTokens);
            } catch (Exception e) {
                System.out.println("[AcessoIa] HF falhou, tentando OpenRouter: " + e.getMessage());
            }
            if (AppSecrets.OPENROUTER_API_KEY != null && !AppSecrets.OPENROUTER_API_KEY.isBlank()) {
                return enviarMensagensOpenRouterComMetadados(mensagens, DEFAULT_MODEL_OPENROUTER, temperatura, maxTokens);
            } else {
                throw new IllegalStateException("Nenhuma API key configurada (Groq, HF ou OpenRouter) em AppSecrets");
            }
        }
        if (modeloEfetivo.startsWith("hf:")) {
            return enviarMensagensHfComMetadados(mensagens, modeloEfetivo.replace("hf:", ""), temperatura, maxTokens);
        } else if (modeloEfetivo.startsWith("groq:") || modeloEfetivo.startsWith("llama") || modeloEfetivo.startsWith("mistral")) {
            return enviarMensagensGroqComMetadados(mensagens, modeloEfetivo.replace("groq:", ""), temperatura, maxTokens);
        } else {
            return enviarMensagensOpenRouterComMetadados(mensagens, modeloEfetivo, temperatura, maxTokens);
        }
    }

    private RespostaIa enviarMensagensOpenRouterComMetadados(List<Map<String, String>> mensagens, String modelo, Double temperatura, Integer maxTokens) throws Exception {
        DESTINO.set(new String[] {"openrouter", modelo});
        if (AppSecrets.OPENROUTER_API_KEY == null || AppSecrets.OPENROUTER_API_KEY.isBlank()) {
            throw new IllegalStateException("OpenRouter API key nao configurada em AppSecrets");
        }
        String json = montarJsonMensagens(modelo, mensagens, temperatura, maxTokens);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENROUTER_API_URL))
                .header("Authorization", "Bearer " + AppSecrets.OPENROUTER_API_KEY)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("HTTP-Referer", "https://ghbam.hospitalit.com.br")
                .header("X-OpenRouter-Title", "GHBAM")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API OpenRouter: " + response.statusCode() + " - " + response.body());
        }
        return montarRespostaComMetadados(response.body(), "openrouter");
    }

    private RespostaIa enviarMensagensGroqComMetadados(List<Map<String, String>> mensagens, String modelo, Double temperatura, Integer maxTokens) throws Exception {
        DESTINO.set(new String[] {"groq", modelo});
        if (AppSecrets.GROQ_API_KEY == null || AppSecrets.GROQ_API_KEY.isBlank()) {
            throw new IllegalStateException("Groq API key nao configurada em AppSecrets");
        }
        String json = montarJsonMensagens(modelo, mensagens, temperatura, maxTokens);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + AppSecrets.GROQ_API_KEY)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API Groq: " + response.statusCode() + " - " + response.body());
        }
        return montarRespostaComMetadados(response.body(), "groq");
    }

    private RespostaIa enviarMensagensHfComMetadados(List<Map<String, String>> mensagens, String modelo, Double temperatura, Integer maxTokens) throws Exception {
        DESTINO.set(new String[] {"huggingface", modelo});
        if (AppSecrets.HF_API_TOKEN == null || AppSecrets.HF_API_TOKEN.isBlank()) {
            throw new IllegalStateException("HF_API_TOKEN nao configurado em AppSecrets");
        }
        String json = montarJsonMensagens(modelo, mensagens, temperatura, maxTokens);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_ROUTER_URL))
                .header("Authorization", "Bearer " + AppSecrets.HF_API_TOKEN)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(25))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro no HF Router: " + response.statusCode() + " - " + response.body());
        }
        return montarRespostaComMetadados(response.body(), "huggingface");
    }

    private RespostaIa montarRespostaComMetadados(String jsonResponse, String provedor) {
        String conteudo = extrairResposta(jsonResponse);
        Integer tokensPrompt = null;
        Integer tokensCompletion = null;
        Integer tokensTotal = null;
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode usage = root.get("usage");
            if (usage != null) {
                if (usage.has("prompt_tokens")) tokensPrompt = usage.get("prompt_tokens").asInt();
                if (usage.has("completion_tokens")) tokensCompletion = usage.get("completion_tokens").asInt();
                if (usage.has("total_tokens")) tokensTotal = usage.get("total_tokens").asInt();
            }
        } catch (Exception ignored) {
            // resposta sem campo "usage" parseavel: metadados de token ficam nulos
        }
        return new RespostaIa(conteudo, provedor, tokensPrompt, tokensCompletion, tokensTotal);
    }

    private String enviarMensagensOpenRouter(List<Map<String, String>> mensagens, String modelo) throws Exception {
        DESTINO.set(new String[] {"openrouter", modelo});
        if (AppSecrets.OPENROUTER_API_KEY == null || AppSecrets.OPENROUTER_API_KEY.isBlank()) {
            throw new IllegalStateException("OpenRouter API key nao configurada em AppSecrets");
        }

        String json = montarJsonMensagens(modelo, mensagens);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENROUTER_API_URL))
                .header("Authorization", "Bearer " + AppSecrets.OPENROUTER_API_KEY)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("HTTP-Referer", "https://ghbam.hospitalit.com.br")
                .header("X-OpenRouter-Title", "GHBAM")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API OpenRouter: " + response.statusCode() + " - " + response.body());
        }
        return extrairResposta(response.body());
    }

    private String enviarMensagensGroq(List<Map<String, String>> mensagens, String modelo) throws Exception {
        DESTINO.set(new String[] {"groq", modelo});
        if (AppSecrets.GROQ_API_KEY == null || AppSecrets.GROQ_API_KEY.isBlank()) {
            throw new IllegalStateException("Groq API key nao configurada em AppSecrets");
        }

        String json = montarJsonMensagens(modelo, mensagens);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + AppSecrets.GROQ_API_KEY)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na API Groq: " + response.statusCode() + " - " + response.body());
        }
        return extrairResposta(response.body());
    }

    private String enviarMensagensHf(List<Map<String, String>> mensagens, String modelo) throws Exception {
        DESTINO.set(new String[] {"huggingface", modelo});
        if (AppSecrets.HF_API_TOKEN == null || AppSecrets.HF_API_TOKEN.isBlank()) {
            throw new IllegalStateException("HF_API_TOKEN nao configurado em AppSecrets");
        }

        String json = montarJsonMensagens(modelo, mensagens);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HF_ROUTER_URL))
                .header("Authorization", "Bearer " + AppSecrets.HF_API_TOKEN)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(25))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro no HF Router: " + response.statusCode() + " - " + response.body());
        }
        return extrairResposta(response.body());
    }

    private String montarJsonSimples(String modelo, String pergunta) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelo);
            ArrayNode messages = body.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", pergunta);
            body.put("max_tokens", 4000);
            body.put("temperature", 0.7);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao montar JSON: " + e.getMessage());
        }
    }

    private String montarJsonMensagens(String modelo, List<Map<String, String>> mensagens) {
        return montarJsonMensagens(modelo, mensagens, null, null);
    }

    private String montarJsonMensagens(String modelo, List<Map<String, String>> mensagens, Double temperatura, Integer maxTokens) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelo);
            ArrayNode msgs = body.putArray("messages");
            for (Map<String, String> m : mensagens) {
                ObjectNode msg = msgs.addObject();
                if (m.get("role") != null) msg.put("role", m.get("role"));
                if (m.get("content") != null) msg.put("content", m.get("content"));
            }
            body.put("max_tokens", maxTokens != null ? maxTokens : 4000);
            body.put("temperature", temperatura != null ? temperatura : 0.7);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao montar JSON: " + e.getMessage());
        }
    }

    private String extrairResposta(String jsonResponse) {
        JsonNode content = null;
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                JsonNode message = first.get("message");
                if (message != null && message.has("content")) {
                    content = message.get("content");
                }
            }
        } catch (Exception e) {
            return "Erro ao processar resposta da API: " + e.getMessage();
        }
        if (content == null) return "Resposta nao encontrada na resposta da API";
        // "content": null (resposta cortada/vazia) virava o texto "null" e era tratado como sucesso;
        // falhar aqui deixa o fallback tentar o proximo provedor.
        if (content.isNull() || content.asText().isBlank()) {
            throw new IllegalStateException("Provedor de IA retornou resposta vazia");
        }
        return content.asText();
    }
}
