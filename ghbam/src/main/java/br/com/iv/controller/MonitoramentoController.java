package br.com.iv.controller;

import br.com.iv.dto.MonitoramentoDto;
import br.com.iv.service.MonitoramentoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("monitoramento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MonitoramentoController {

    @Inject
    MonitoramentoService monitoramentoService;

    @GET
    @Path("/tempo-real/{unidade}")
    public Response tempoReal(@PathParam("unidade") String unidade) {
        MonitoramentoDto dto = monitoramentoService.monitorar(unidade);
        return Response.ok(dto).build();
    }
}
