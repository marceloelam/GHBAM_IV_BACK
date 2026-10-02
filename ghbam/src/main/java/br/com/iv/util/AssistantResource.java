package br.com.iv.util;

import br.gov.secure.AppSecrets;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ApplicationScoped
@Path("/api/assistant")
public class AssistantResource {

    private final ObjectMapper mapper = new ObjectMapper();
    // Substitua pelo endpoint/model correto da sua conta Gemini/Vertex
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash";

    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response query(Map<String, Object> payload) {
        try {
            String userText = (String) payload.getOrDefault("text", "");
            String route = (String) payload.getOrDefault("route", "");
            String userRole = (String) payload.getOrDefault("userRole", "");

            if (AppSecrets.GOOGLE_GENAI_API_KEY == null || AppSecrets.GOOGLE_GENAI_API_KEY.isBlank()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("error", "GOOGLE_GENAI_API_KEY nao configurado"))
                        .build();
            }
// Prompt: peca resposta estrita em JSON
            String systemInstructions = "Voce e um assistente do sistema GHBAM. Responda SOMENTE em JSON com campos 'reply' (string) e 'actions' (array). "
                    + "Cada action deve ter { type: 'navigate'|'open_modal'|'fill', path?: '/x', selector?: '#id', value?: '...' }. "
                    + "Se nao houver actions, retorne actions: []. Nao inclua texto fora do JSON.";

            String prompt = systemInstructions + "\nContexto: rota=" + route + ", role=" + userRole + "\nPergunta: " + userText;

            // Monta body conforme a API usada (ajuste para a versao do endpoint)
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("prompt", prompt);
            // se a API exigir campos diferentes (ex: input, temperature), adicione aqui

            String requestJson = mapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_ENDPOINT))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");

            // Use API key as query param se aplicavel, ou Authorization Bearer com token
            if (AppSecrets.GOOGLE_GENAI_API_KEY != null && !AppSecrets.GOOGLE_GENAI_API_KEY.isBlank()) {
                // Exemplo: endpoint que aceita key via ?key=API_KEY
                String urlWithKey = GEMINI_ENDPOINT + "?key=" + AppSecrets.GOOGLE_GENAI_API_KEY;
                builder.uri(URI.create(urlWithKey));
            } else {
                // Alternativa: usar ADC / Bearer token. Aqui assumimos API key disponivel.
            }

            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(requestJson)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                // A resposta do Gemini pode ter estrutura propria; extrair o texto gerado
                // Exemplo leve: procurar campo 'outputText' ou 'candidates'
                // Tente mapear para JSON e extrair o texto gerado
                Map<?,?> respMap = mapper.readValue(body, Map.class);
                String modelText = extractModelText(respMap);

                // Agora parsear o JSON que pedimos ao modelo (o modelo deve retornar valid JSON)
                Map<?,?> assistantJson;
                try {
                    assistantJson = mapper.readValue(modelText, Map.class);
                } catch (Exception ex) {
                    // fallback: devolver reply simples
                    ObjectNode fallback = mapper.createObjectNode();
                    fallback.put("reply", modelText);
                    fallback.putArray("actions");
                    return Response.ok(mapper.writeValueAsString(fallback)).build();
                }

                String out = mapper.writeValueAsString(assistantJson);
                return Response.ok(out).build();

            } else {
                return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(Map.of("error", "Erro ao consultar Gemini", "status", response.statusCode()))
                        .build();
            }

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", "Erro interno ao consultar assistente")).build();
        }
    }

    private String extractModelText(Map<?,?> respMap) {
        // Tente localizar texto em keys comuns. Ajuste conforme a resposta real.
        try {
            if (respMap.containsKey("output")) {
                Object out = respMap.get("output");
                if (out instanceof Map) {
                    Map<?,?> outMap = (Map<?,?>) out;
                    if (outMap.containsKey("text")) return outMap.get("text").toString();
                }
            }
            if (respMap.containsKey("candidates")) {
                Object c = respMap.get("candidates");
                if (c instanceof Iterable) {
                    for (Object item : (Iterable<?>) c) {
                        if (item instanceof Map) {
                            Map<?,?> m = (Map<?,?>) item;
                            if (m.containsKey("content")) return m.get("content").toString();
                            if (m.containsKey("output")) return m.get("output").toString();
                        }
                    }
                }
            }
            // fallback: raw toString
            return respMap.toString();
        } catch (Exception ex) {
            return respMap.toString();
        }
    }
}