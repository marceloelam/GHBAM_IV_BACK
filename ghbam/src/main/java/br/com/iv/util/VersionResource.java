/*
 * VersionResource.java
 * --------------------
 * Arquivo exemplo para Quarkus/Jakarta que expõe GET /api/version com ETag.
 * ATENÇÃO: altere o package abaixo para o package do seu projeto antes de colar
 * em src/main/java/<seu_package_path>/VersionResource.java
 */

package br.com.iv.util; // ajustado para o package solicitado

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Map;

// Este arquivo é um exemplo. Para evitar conflito com outras classes que
// possam expor o mesmo endpoint, alterei o path exemplo para "/api/version/example".
@Path("/api/version/example")
@ApplicationScoped
public class VersionResource {

    // Pode ser passado no build/execution com -Dapp.version=<commit>
    @ConfigProperty(name = "app.version", defaultValue = "")
    String appVersion;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersion(@Context Request request) {
        String commit = (appVersion != null && !appVersion.isBlank()) ? appVersion : "";
        String timestamp = Instant.now().toString();

        // preferir commit como base do ETag quando disponível
        String etagSource = (commit.isEmpty() ? timestamp : commit);
        EntityTag etag = new EntityTag(Integer.toString(etagSource.hashCode()));

        // retorna 304 se If-None-Match bater
        Response.ResponseBuilder precondition = request.evaluatePreconditions(etag);
        if (precondition != null) {
            return precondition.build(); // 304
        }

        Map<String, String> body = Map.of(
            "timestamp", timestamp,
            "commit", (commit.isEmpty() ? timestamp : commit)
        );

        return Response.ok(body)
                       .tag(etag)
                       .build();
    }
}
