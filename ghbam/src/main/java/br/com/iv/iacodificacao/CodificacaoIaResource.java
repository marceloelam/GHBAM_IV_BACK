package br.com.iv.iacodificacao;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Sugestao de CID e procedimento para o laudo de AIH (modelo local, desligado por
 * padrao). Permissoes em RoutePermissionCatalog ("ia-codificacao"), modulo IA.
 */
@Path("ia-codificacao")
@Produces(MediaType.APPLICATION_JSON)
public class CodificacaoIaResource {

    public record PedidoSugestao(String texto, Integer idbam) {
    }

    @Inject
    CodificacaoIaService service;

    @GET
    @Path("status")
    public Response status() {
        return Response.ok(service.status()).build();
    }

    @POST
    @Path("sugerir")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sugerir(PedidoSugestao pedido) {
        try {
            return Response.ok(service.sugerir(pedido == null ? null : pedido.texto(),
                    pedido == null ? null : pedido.idbam())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        }
    }
}
