package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.PacientesImagensPacsDto;
import br.com.iv.dto.PacsListarRequestDto;
import br.com.iv.service.PacsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("imagens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PacsController {

    @Inject
    PacsService pacsService;

    @POST
    @Path("/listar_tudo_lazy_numero_pacs")
    public Response listarPorNumero(PacsListarRequestDto request) {
        List<PacientesImagensPacsDto> result = pacsService.listarPorNumero(
                Integer.parseInt(request.getNome()),
                request.getFirst(),
                request.getPageSize());
        return Response.ok(result).build();
    }

    @POST
    @Path("/listar_tudo_lazy_pacs")
    public Response listarPorNome(PacsListarRequestDto request) {
        List<PacientesImagensPacsDto> result = pacsService.listarPorNome(
                request.getNome(),
                request.getFirst(),
                request.getPageSize());
        return Response.ok(result).build();
    }

    @GET
    @Path("/bam/{bam}")
    public Response listarPorBam(@PathParam("bam") int bam) {
        List<PacientesImagensPacsDto> result = pacsService.listarPorBam(bam);
        return Response.ok(result).build();
    }
}
