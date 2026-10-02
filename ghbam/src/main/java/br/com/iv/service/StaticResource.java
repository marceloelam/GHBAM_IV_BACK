package br.com.iv.service;

import java.nio.file.Files;
import java.nio.file.Paths;

import io.quarkus.vertx.web.Route;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StaticResource {

    @Route(path = "/img/:fileName", methods = Route.HttpMethod.GET)
    void serveFile(RoutingContext rc) {
        String fileName = rc.pathParam("fileName");
        String filePath = "src/main/resources/img/" + fileName;

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            String contentType = Files.probeContentType(Paths.get(filePath));

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            rc.response().putHeader("Content-Type", contentType).end(Buffer.buffer(fileBytes));
        } catch (Exception e) {
            rc.response().setStatusCode(404).end("Arquivo não encontrado");
        }
    }
}