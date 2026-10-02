package br.com.iv.controller;

import br.com.iv.dto.ZamedEnvioRespostaDto;
import br.com.iv.dto.ZamedSolicitacaoDto;
import br.com.iv.service.ZamedEnvioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("zamed")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ZamedController {

    @Inject
    ZamedEnvioService zamedEnvioService;

    @POST
    @Path("/solicitacoes")
    public Response enviarSolicitacao(ZamedSolicitacaoDto dto) {
        try {
            ZamedEnvioRespostaDto resposta = zamedEnvioService.enviar(dto);
            if (!resposta.isEnviado()) {
                return Response.status(Response.Status.BAD_GATEWAY).entity(resposta).build();
            }
            return Response.ok(resposta).build();
        } catch (IllegalArgumentException e) {
            ZamedEnvioRespostaDto resposta = new ZamedEnvioRespostaDto();
            resposta.setEnviado(false);
            resposta.setStatusCode(400);
            resposta.setMensagem(e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(resposta).build();
        }
    }
}
