package br.com.iv.controller;

import java.util.Optional;

import br.com.iv.dto.ProcedimentoConsultorioPrecoDto;
import br.com.iv.dto.ProcedimentoConsultorioDto;
import br.com.iv.entity.ProcedimentoConsultorioPreco;
import br.com.iv.service.ProcedimentoConsultorioPrecoService;
import br.com.iv.service.ProcedimentoConsultorioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("procedimentoconsultorio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProcedimentoConsultorioController {

    @Inject
    ProcedimentoConsultorioService service;

    @Inject
    ProcedimentoConsultorioPrecoService precoService;

    @GET
    @Path("/unidade/{unidade}")
    public Response listarPorUnidade(@PathParam("unidade") String unidade, @QueryParam("ativos") String ativos) {
        boolean apenasAtivos = ativos == null || !"false".equalsIgnoreCase(ativos);
        return Response.ok(service.listarPorUnidade(unidade, apenasAtivos)).build();
    }

    @POST
    @Path("/unidade/{unidade}/popular-padrao")
    public Response popularPadrao(@PathParam("unidade") String unidade, @QueryParam("usuario") String usuario) {
        return Response.status(Response.Status.CREATED).entity(service.popularCatalogoPadrao(unidade, usuario)).build();
    }

    @POST
    public Response salvar(ProcedimentoConsultorioDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.status(Response.Status.CREATED).entity(service.salvar(dto)).build();
    }

    @PUT
    @Path("{id}")
    public Response atualizar(@PathParam("id") int id, ProcedimentoConsultorioDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.ok(service.atualizar(id, dto)).build();
    }

    @PUT
    @Path("{id}/ativo")
    public Response alterarAtivo(@PathParam("id") int id, ProcedimentoConsultorioDto dto) {
        return Response.ok(service.alterarAtivo(id, dto)).build();
    }

    @GET
    @Path("{id}/precos/unidade/{unidade}")
    public Response listarPrecos(@PathParam("id") int id, @PathParam("unidade") String unidade) {
        return Response.ok(precoService.listarPorProcedimento(id, unidade)).build();
    }

    @GET
    @Path("{id}/preco/unidade/{unidade}")
    public Response buscarMelhorPreco(@PathParam("id") int id, @PathParam("unidade") String unidade,
            @QueryParam("tipoCobranca") String tipoCobranca, @QueryParam("convenio") String convenio,
            @QueryParam("plano") String plano) {
        Optional<ProcedimentoConsultorioPreco> preco = precoService.buscarMelhorPreco(id, unidade, tipoCobranca, convenio, plano);
        if (preco.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(preco.get()).build();
    }

    @POST
    @Path("{id}/precos")
    public Response salvarPreco(@PathParam("id") int id, ProcedimentoConsultorioPrecoDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        dto.setIdprocedimentoConsultorio(id);
        return Response.status(Response.Status.CREATED).entity(precoService.salvar(dto)).build();
    }

    @PUT
    @Path("precos/{idPreco}")
    public Response atualizarPreco(@PathParam("idPreco") int idPreco, ProcedimentoConsultorioPrecoDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Payload ausente").build();
        }
        return Response.ok(precoService.atualizar(idPreco, dto)).build();
    }

    @PUT
    @Path("precos/{idPreco}/ativo")
    public Response alterarPrecoAtivo(@PathParam("idPreco") int idPreco, ProcedimentoConsultorioPrecoDto dto) {
        return Response.ok(precoService.alterarAtivo(idPreco, dto)).build();
    }
}
