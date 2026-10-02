package br.com.iv.controller;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

@Path("health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthController {

    @Inject
    AgroalDataSource dataSource;

    @GET
    @Path("live")
    public Response live() {
        return Response.ok(Map.of("status", "UP")).build();
    }

    @GET
    @Path("ready")
    public Response ready() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select 1");
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return Response.ok(Map.of("status", "UP", "database", "UP")).build();
            }
            return unavailable("Banco de dados nao retornou resultado.");
        } catch (Exception e) {
            return unavailable("Banco de dados indisponivel.");
        }
    }

    private Response unavailable(String mensagem) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("status", "DOWN", "database", "DOWN", "mensagem", mensagem))
                .build();
    }
}
