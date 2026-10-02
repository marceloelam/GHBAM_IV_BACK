package br.com.iv.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.service.ProcedimentosPasrvService;

@Path("procedimentosPasrv")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProcedimentosPasrvController {

    @Inject
    ProcedimentosPasrvService procedimentosPasrvService;

    @GET
    @Path("/codigo/{procedimento}")
    public Response pegarProcedimento(@PathParam("procedimento") String procedimento) {
        Object resultado = procedimentosPasrvService.procedimentosPasrvProcesso(procedimento);

        if (resultado == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Procedimento não encontrado nem em PASRV nem em RX")
                    .build();
        }

        return Response.ok(resultado).build();
    }
}
