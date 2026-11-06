package br.com.iv.controller;

import br.com.iv.dto.TemaVisualDto;
import br.com.iv.service.TemaVisualService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("tema-visual")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TemaVisualController {

    @Inject
    TemaVisualService temaVisualService;

    @GET
    public Response buscarPorUnidade(@QueryParam("unidade") String unidade) {
        return temaVisualService.buscarPorUnidade(unidade)
                .map(tema -> Response.ok(tema).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @GET
    @Path("login")
    public Response buscarParaLogin(@QueryParam("unidade") String unidade) {
        return temaVisualService.buscarParaLogin(unidade)
                .map(tema -> Response.ok(tema).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @POST
    public Response salvar(TemaVisualDto dto) {
        try {
            TemaVisualDto salvo = temaVisualService.salvar(dto);
            return Response.ok(salvo).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErroTemaVisual(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErroTemaVisual("Nao foi possivel salvar o tema visual da unidade."))
                    .build();
        }
    }

    public static class ErroTemaVisual {
        public String mensagem;

        public ErroTemaVisual(String mensagem) {
            this.mensagem = mensagem;
        }
    }
}
