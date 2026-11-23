package br.com.iv.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.service.SigtapCidService;

/**
 * CIDs que o SIGTAP aceita para um procedimento - usado pelas telas que lancam
 * procedimento no faturamento (multiprofissional e odontologia) para exigir e oferecer
 * so CID compativel. Permissoes em RoutePermissionCatalog ("sigtap-cid").
 */
@Path("sigtap-cid")
@Produces(MediaType.APPLICATION_JSON)
public class SigtapCidController {

    @Inject
    SigtapCidService service;

    @GET
    @Path("{procedimento}")
    public Response compativeis(@PathParam("procedimento") String procedimento,
                                @QueryParam("q") String filtro,
                                @QueryParam("bam") Integer bam) {
        Map<String, Object> r = new LinkedHashMap<>();
        boolean exige = service.exigeCid(procedimento, null);
        r.put("exigeCid", exige);
        r.put("competencia", service.competenciaVigente(null));
        r.put("doEpisodio", exige ? service.doEpisodioCompativeis(procedimento, bam) : List.of());
        r.put("cids", exige ? service.listar(procedimento, filtro, 50) : List.of());
        return Response.ok(r).build();
    }
}
