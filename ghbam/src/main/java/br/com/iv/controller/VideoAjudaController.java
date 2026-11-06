package br.com.iv.controller;

import java.util.Map;
import java.util.regex.Pattern;

import br.com.iv.service.VideoAjudaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Videos de ajuda das telas. Basta estar autenticado (RbacRequestFilter): sao material de treinamento. */
@Path("videos-ajuda")
@Produces(MediaType.APPLICATION_JSON)
public class VideoAjudaController {

    private static final Pattern CODIGO = Pattern.compile("^\\d{2}\\.\\d{2}$");

    @Inject
    VideoAjudaService service;

    @GET
    @Path("lista")
    public Response lista() {
        return Response.ok(service.lista()).build();
    }

    @GET
    @Path("link")
    public Response link(@QueryParam("codigo") String codigo) {
        if (codigo == null || !CODIGO.matcher(codigo).matches()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Codigo invalido")).build();
        }
        if (!service.habilitado()) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("erro", "Videos indisponiveis")).build();
        }
        return Response.ok(Map.of("url", service.link(codigo))).build();
    }
}
