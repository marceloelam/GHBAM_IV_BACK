package br.com.iv.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

@ApplicationScoped
public class StaticResourcesConfig extends AbstractVerticle {

    public void init(@Observes Vertx vertx) {
        Router router = Router.router(vertx);

        // Servir recursos estáticos do diretório "img" e suas subpastas
        router.route("/img/*").handler(StaticHandler.create("META-INF/resources/img"));

        // Servir recursos estáticos do diretório "docs"
        router.route("/docs/*").handler(StaticHandler.create("META-INF/resources/docs"));

        vertx.createHttpServer().requestHandler(router).listen(8080, result -> {
            if (result.succeeded()) {
                System.out.println("Servidor está rodando em http://localhost:8080");
            } else {
                System.err.println("Erro ao iniciar o servidor: " + result.cause());
            }
        });
    }
}

