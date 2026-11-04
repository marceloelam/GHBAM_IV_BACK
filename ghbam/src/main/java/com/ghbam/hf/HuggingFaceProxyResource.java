package com.ghbam.hf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.iv.iaauditoria.IaAuditoriaContexto;
import br.com.iv.iaauditoria.IaAuditoriaService;
import br.com.iv.util.AcessoIa;
import br.gov.secure.AppSecrets;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
@Path("/api/hf")
public class HuggingFaceProxyResource {

    @ConfigProperty(name = "hf.router.url", defaultValue = "https://router.huggingface.co")
    String hfRouterUrl;

    @ConfigProperty(name = "hf.model.map")
    Optional<String> hfModelMapRaw;

    @ConfigProperty(name = "hf.fallback.model")
    Optional<String> hfFallbackModel;

    @ConfigProperty(name = "hf.request.timeout.ms", defaultValue = "30000")
    int requestTimeoutMs;

    @ConfigProperty(name = "hf.request.retries", defaultValue = "2")
    int maxRetries;

    // Provedor de reserva quando o Hugging Face fica sem credito ou indisponivel. Vazio desliga.
    @ConfigProperty(name = "hf.reserva.groq.model", defaultValue = "")
    String reservaModelo;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final int TIMEOUT_LISTA_MS = 15000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Map<String, String> modelMap = Map.of();

    @Inject
    Instance<IaAuditoriaService> auditoria;

    @POST
    @Path("/models/{model: .+}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyModel(@PathParam("model") String model, String body, @QueryParam("semReserva") boolean semReserva, @QueryParam("soModeloValidado") boolean soModeloValidado) {
        long inicio = System.nanoTime();
        try {
            // resolver nome amigável para id real do modelo
            // tolerar sufixos de revisão que o front pode anexar (ex: ":1")
            String resolved;
            if (modelMap.containsKey(model)) {
                resolved = modelMap.get(model);
            } else {
                // remover sufixo final do tipo :<token> (não confundir com barras)
                String sanitized = model.replaceFirst(":[^/]+$", "");
                if (!sanitized.equals(model) && modelMap.containsKey(sanitized)) {
                    resolved = modelMap.get(sanitized);
                } else {
                    resolved = sanitized;
                }
            }

            // validar existência do modelo antes de encaminhar
            Response validate = validateModelExists(resolved);
            if (validate != null) return validate;

            // router HF usa /v1/chat/completions com model no body (provider: featherless-ai)
            String routerBase = hfRouterUrl.endsWith("/") ? hfRouterUrl.substring(0, hfRouterUrl.length()-1) : hfRouterUrl;
            URI uri = URI.create(routerBase + "/v1/chat/completions");
            System.out.println("[HuggingFaceProxy] URL=" + uri + " model=" + resolved);
            String requestBody = toOpenAiChatPayload(body, resolved);
            HttpRequest.Builder reqb = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            String hfToken = AppSecrets.HF_API_TOKEN;
            if (hfToken != null && !hfToken.isBlank()) {
                reqb.header("Authorization", "Bearer " + hfToken);
            }

            HttpRequest request = reqb.build();

            // retries para 5xx / timeouts
            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = resp.statusCode();
                    String respBody = resp.body() == null ? "" : resp.body();
                    String contentType = resp.headers().firstValue("content-type").orElse(MediaType.APPLICATION_JSON);

                    // Nunca logar o corpo: e texto clinico gerado para a evolucao do paciente.
                    System.out.println("[HuggingFaceProxy] status=" + statusCode + " bytes=" + respBody.length());

                    // Se o HF respondeu que o modelo não existe (404/410) e existe fallback configurado,
                    // tentar encaminhar para o modelo de fallback uma vez antes de devolver erro.
                    if ((statusCode == 404 || statusCode == 410) && !effectiveModel().isBlank() && attempt == 1) {
                        try {
                            String fb = resolveModel(effectiveModel());
                            System.out.println("[HuggingFaceProxy] tentando fallback model=" + fb);
                            String rBase = hfRouterUrl.endsWith("/") ? hfRouterUrl.substring(0, hfRouterUrl.length()-1) : hfRouterUrl;
                            URI fbUri = URI.create(rBase + "/v1/chat/completions");
                            System.out.println("[HuggingFaceProxy] fallback URL=" + fbUri + " model=" + fb);
                            HttpRequest.Builder frb = HttpRequest.newBuilder(fbUri)
                                    .header("Content-Type", "application/json")
                                    .timeout(Duration.ofMillis(requestTimeoutMs))
                                    .POST(HttpRequest.BodyPublishers.ofString(toOpenAiChatPayload(body, fb)));
                            String hfToken2 = AppSecrets.HF_API_TOKEN;
                            if (hfToken2 != null && !hfToken2.isBlank()) frb.header("Authorization", "Bearer " + hfToken2);
                            HttpRequest frequest = frb.build();
                            HttpResponse<String> fresp = client.send(frequest, HttpResponse.BodyHandlers.ofString());
                            int fsc = fresp.statusCode();
                            String fbody = fresp.body() == null ? "" : fresp.body();
                            String fct = fresp.headers().firstValue("content-type").orElse(MediaType.APPLICATION_JSON);
                            System.out.println("[HuggingFaceProxy] fallback status=" + fsc + " bytes=" + fbody.length());
                            auditar(body, fb, fsc, fbody, null, inicio);
                            return Response.status(fsc).entity(fbody).type(fct).build();
                        } catch (Exception fe) {
                            System.out.println("[HuggingFaceProxy] fallback failed: " + fe.getMessage());
                            // continuar e retornar a resposta original abaixo
                        }
                    }

                    // repassar resposta (4xx/5xx do HF serão encaminhados)
                    auditar(body, resolved, statusCode, respBody, null, inicio);
                    // 402: sem credito; 429: limite; 5xx: fora do ar. semReserva=true devolve o erro original, sem trocar de modelo.
                    if (!semReserva && (statusCode == 402 || statusCode == 429 || statusCode >= 500)) {
                        Response reserva = consultarReserva(body, inicio, soModeloValidado);
                        if (reserva != null) return reserva;
                    }
                    return Response.status(statusCode)
                            .entity(respBody)
                            .type(contentType)
                            .build();
                } catch (Exception e) {
                    // para erros temporários, tentar novamente com backoff
                    if (attempt <= maxRetries) {
                        try {
                            long backoff = 250L * (1L << (attempt - 1));
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw e;
                }
            }
        } catch (Exception e) {
                e.printStackTrace();
                auditar(body, model, 0, null, e, inicio);
                if (!semReserva) {
                    Response reserva = consultarReserva(body, inicio, soModeloValidado);
                    if (reserva != null) return reserva;
                }
                // Stack trace fica so no log do servidor; o navegador recebe mensagem generica.
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Falha ao consultar o modelo de IA. Tente novamente ou acione a TI se persistir.")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    /**
     * Ordem da reserva, no mesmo formato OpenAI que as telas ja leem: Groq, depois a lista do OpenRouter.
     * soModeloValidado (triagem) para no Groq: os modelos da lista nao passaram pelos casos de teste de triagem.
     * Devolve null se nenhum responder: vale o erro original.
     */
    private Response consultarReserva(String body, long inicio, boolean soModeloValidado) {
        if (reservaModelo != null && !reservaModelo.isBlank()) {
            Response groq = tentarReserva("groq", GROQ_URL, AppSecrets.GROQ_API_KEY, reservaModelo, body, requestTimeoutMs, inicio);
            if (groq != null) return groq;
        }
        if (soModeloValidado) return null;
        for (String modelo : AcessoIa.listaModelos(AppSecrets.OPENROUTER_MODELS)) {
            Response reserva = tentarReserva("openrouter", OPENROUTER_URL, AppSecrets.OPENROUTER_API_KEY, modelo, body, TIMEOUT_LISTA_MS, inicio);
            if (reserva != null) return reserva;
        }
        return null;
    }

    private Response tentarReserva(String provedor, String url, String chave, String modelo, String body, int timeoutMs, long inicio) {
        if (chave == null || chave.isBlank()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + chave)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadReserva(body, modelo, "openrouter".equals(provedor))))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            String respBody = resp.body() == null ? "" : resp.body();
            System.out.println("[HuggingFaceProxy] reserva " + provedor + " model=" + modelo + " status=" + status + " bytes=" + respBody.length());
            auditar(provedor, body, modelo, status, respBody, null, inicio);
            if (status != 200 || !temConteudo(respBody)) return null;
            return Response.ok(respBody).type(MediaType.APPLICATION_JSON).header("X-GHBAM-IA-Provedor", provedor).build();
        } catch (Exception e) {
            System.out.println("[HuggingFaceProxy] reserva " + provedor + " falhou: " + e.getClass().getSimpleName());
            auditar(provedor, body, modelo, 0, null, e, inicio);
            return null;
        }
    }

    /** Resposta 200 sem texto (modelo gastou o limite raciocinando) conta como falha: tenta o proximo. */
    boolean temConteudo(String respBody) throws Exception {
        return !objectMapper.readTree(respBody).path("choices").path(0).path("message").path("content").asText("").isBlank();
    }

    String payloadReserva(String body) throws Exception {
        return payloadReserva(body, reservaModelo, false);
    }

    /** Monta o pedido da reserva e tira os marcadores de prompt que as telas escrevem para o modelo do Hugging Face. */
    String payloadReserva(String body, String modelo, boolean exigirPrivacidade) throws Exception {
        JsonNode entrada = body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        ObjectNode saida = objectMapper.createObjectNode();
        saida.put("model", modelo);
        ArrayNode mensagens = saida.putArray("messages");
        if (entrada.path("messages").isArray()) {
            for (JsonNode m : entrada.path("messages")) {
                mensagens.addObject().put("role", m.path("role").asText("user")).put("content", limparMarcadores(m.path("content").asText("")));
            }
        } else {
            mensagens.addObject().put("role", "user").put("content", limparMarcadores(entrada.path("inputs").asText("")));
        }
        int maximo = entrada.path("max_tokens").asInt(entrada.path("parameters").path("max_new_tokens").asInt(512));
        // O gpt-oss gasta o limite raciocinando: com os 200 tokens da triagem a resposta vinha vazia.
        if (modelo.contains("gpt-oss")) {
            saida.put("reasoning_effort", "low");
            maximo = Math.max(maximo, 1024);
        }
        // O OpenRouter so encaminha para provedores que nao guardam nem treinam com os dados.
        if (exigirPrivacidade) saida.putObject("provider").put("data_collection", "deny");
        saida.put("max_tokens", maximo);
        if (entrada.has("temperature")) saida.set("temperature", entrada.get("temperature"));
        return objectMapper.writeValueAsString(saida);
    }

    static String limparMarcadores(String texto) {
        return texto.replaceAll("<\\|im_(start|end)\\|>(system|user|assistant)?\\s*", "\n")
                .replaceAll("\\[/?INST\\]", "")
                .trim();
    }

    private void auditar(String body, String modelo, int statusHttp, String respBody, Exception erro, long inicio) {
        auditar("huggingface", body, modelo, statusHttp, respBody, erro, inicio);
    }

    private void auditar(String provedor, String body, String modelo, int statusHttp, String respBody, Exception erro, long inicio) {
        if (auditoria == null || !auditoria.isResolvable()) return;
        IaAuditoriaContexto.registrar("HF_PROXY_EVOLUCAO", null, null, null);
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "raw");
        msg.put("content", body);
        Exception falha = erro != null ? erro
                : (statusHttp != 200 ? new RuntimeException("HTTP " + statusHttp + " do provedor " + provedor) : null);
        auditoria.get().registrar(new IaAuditoriaService.Chamada(
                java.util.List.of(msg), provedor, modelo,
                falha == null ? respBody : null, falha, inicio, null, null, "HuggingFaceProxyResource"));
    }

    /** Retorna o modelo efetivo: AppSecrets.HF_MODEL tem prioridade; fallback vem do application.properties. */
    private String effectiveModel() {
        String fromSecrets = AppSecrets.HF_MODEL;
        if (fromSecrets != null && !fromSecrets.isBlank()) return fromSecrets.trim();
        return hfFallbackModel.filter(s -> !s.isBlank()).orElse("");
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        Map<String, Object> status = new HashMap<>();
        status.put("router", hfRouterUrl);
        String model = effectiveModel();
        status.put("fallbackModel", model);

        if (model.isBlank()) {
            status.put("fallbackModelStatus", "not-configured");
            return Response.ok(status).build();
        }

        try {
            String fb = model;
            Response v = validateModelExists(fb);
            if (v == null) {
                status.put("fallbackModelStatus", "ok");
            } else {
                status.put("fallbackModelStatus", "invalid");
                status.put("fallbackModelCheck", v.getEntity());
            }
        } catch (Exception e) {
            status.put("fallbackModelStatus", "error");
            status.put("error", e.getMessage());
        }

        return Response.ok(status).build();
    }

    private String resolveModel(String model) {
        if (modelMap.containsKey(model)) return modelMap.get(model);
        return model;
    }

    /**
     * Converte o payload legado {inputs, parameters} para o formato OpenAI chat
     * e inclui o campo "model" obrigatório (exigido pelo provider featherless-ai).
     */
    private String toOpenAiChatPayload(String body, String model) {
        String modelJson = model != null && !model.isBlank() ? "\"model\":\"" + model + "\"," : "";
        if (body == null || body.isBlank()) {
            return "{" + modelJson + "\"messages\":[{\"role\":\"user\",\"content\":\"\"}],\"max_tokens\":512}";
        }
        // Se já estiver no formato OpenAI (tem "messages"), garantir que tem o campo model
        if (body.contains("\"messages\"")) {
            if (!body.contains("\"model\"") && !modelJson.isEmpty()) {
                // Inserir model logo após a abertura do objeto
                return "{" + modelJson + body.substring(1);
            }
            return body;
        }
        // Extrair o campo "inputs" do payload legado
        java.util.regex.Matcher m = Pattern.compile("\\\"inputs\\\"\\s*:\\s*\\\"((?:[^\\\\\"]|\\\\\\.)*)\"").matcher(body);
        String userContent = m.find() ? m.group(1).replace("\\\\\"", "\"") : body;
        // Extrair max_new_tokens se existir
        java.util.regex.Matcher mt = Pattern.compile("\\\"max_new_tokens\\\"\\s*:\\s*(\\d+)").matcher(body);
        int maxTokens = mt.find() ? Integer.parseInt(mt.group(1)) : 512;
        return String.format(
            "{" + modelJson + "\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":%d}",
            userContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", ""),
            maxTokens
        );
    }

    private Response validateModelExists(String model) {
        try {
            URI meta = URI.create("https://huggingface.co/api/models/" + model);
            HttpRequest.Builder rb = HttpRequest.newBuilder(meta)
                    .GET()
                    .timeout(Duration.ofMillis(Math.min(requestTimeoutMs, 5000)));

            String token = AppSecrets.HF_API_TOKEN;
            if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);

            HttpRequest r = rb.build();
            HttpResponse<String> resp = client.send(r, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc == 404) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"modelo não encontrado: " + model + "\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
            if (sc == 401 || sc == 403) {
                return Response.status(Response.Status.BAD_GATEWAY)
                        .entity("{\"error\":\"token inválido ou sem permissão para acessar o modelo\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
            return null; // ok
        } catch (Exception e) {
            // não bloquear a requisição por falha de verificação de metadata — retornar null para prosseguir
            System.out.println("[HuggingFaceProxy] model validation error: " + e.getMessage());
            return null;
        }
    }

    @PostConstruct
    void init() {
        if (hfModelMapRaw.isPresent() && !hfModelMapRaw.get().isBlank()) {
            Map<String, String> tmp = new HashMap<>();
            String[] pairs = hfModelMapRaw.get().split(",");
            for (String p : pairs) {
                String[] kv = p.split(":" , 2);
                if (kv.length == 2) tmp.put(kv[0].trim(), kv[1].trim());
            }
            modelMap = Map.copyOf(tmp);
        }
    }
}