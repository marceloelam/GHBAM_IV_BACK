package br.com.iv.util;

import br.gov.secure.AppSecrets;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/genai")
public class Gemini {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response callGoogleGenAI(String inputJson) {
        try {
            if (AppSecrets.GOOGLE_GENAI_API_KEY == null || AppSecrets.GOOGLE_GENAI_API_KEY.isBlank()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("GOOGLE_GENAI_API_KEY nao configurado")
                    .build();
            }
            // Configuracao do cliente HTTP
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new java.net.URI("https://api.google.com/genai")) // Substitua pela URL da API do Google GenAI
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AppSecrets.GOOGLE_GENAI_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(inputJson))
                .build();

            // Enviar a requisicao e obter a resposta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Retornar a resposta para o cliente React
            return Response.status(response.statusCode()).entity(response.body()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Erro ao chamar a API de IA.").build();
        }
    }
}
