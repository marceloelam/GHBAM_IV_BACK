package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.dto.TissContaDto;
import br.com.iv.dto.TissContaItemDto;
import br.com.iv.entity.TissConta;
import br.com.iv.entity.TissContaItem;
import br.com.iv.service.TissContaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("tiss/contas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TissContaController {

    @Inject
    TissContaService tissContaService;

    @GET
    @Path("/data1/{d1}/data2/{d2}/unidade/{unidade}")
    public Response listarPorPeriodoUnidade(@PathParam("d1") String d1, @PathParam("d2") String d2,
            @PathParam("unidade") String unidade) {
        List<TissConta> contas = tissContaService.listarPorPeriodoUnidade(d1, d2, unidade);
        return Response.ok(contas).build();
    }

    @GET
    @Path("/bam/{bam}")
    public Response listarPorBam(@PathParam("bam") int bam) {
        List<TissConta> contas = tissContaService.listarPorBam(bam);
        return Response.ok(contas).build();
    }

    @GET
    @Path("/{id}")
    public Response buscarConta(@PathParam("id") int id) {
        return tissContaService.buscarConta(id)
                .map(conta -> Response.ok(conta).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).entity("Conta TISS nao encontrada").build());
    }

    @GET
    @Path("/{id}/itens")
    public Response listarItens(@PathParam("id") int id) {
        List<TissContaItem> itens = tissContaService.listarItens(id);
        return Response.ok(itens).build();
    }

    @GET
    @Path("/conciliacao-bpa/data1/{d1}/data2/{d2}/unidade/{unidade}")
    public Response conciliarBpaIndividualizado(@PathParam("d1") String d1, @PathParam("d2") String d2,
            @PathParam("unidade") String unidade) {
        Map<String, Object> resultado = tissContaService.conciliarBpaIndividualizado(d1, d2, unidade);
        return Response.ok(resultado).build();
    }

    @POST
    @Path("/capturar-periodo/data1/{d1}/data2/{d2}/unidade/{unidade}")
    public Response capturarPeriodo(@PathParam("d1") String d1, @PathParam("d2") String d2,
            @PathParam("unidade") String unidade, TissContaDto dto) {
        try {
            Map<String, Object> resultado = tissContaService.capturarPeriodo(d1, d2, unidade, dto);
            return Response.ok(resultado).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/aplicar-padrao-periodo/data1/{d1}/data2/{d2}/unidade/{unidade}")
    public Response aplicarPadraoPeriodo(@PathParam("d1") String d1, @PathParam("d2") String d2,
            @PathParam("unidade") String unidade, TissContaDto dto) {
        try {
            Map<String, Object> resultado = tissContaService.aplicarPadraoPeriodo(d1, d2, unidade, dto);
            return Response.ok(resultado).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/validar-periodo/data1/{d1}/data2/{d2}/unidade/{unidade}")
    public Response validarPeriodo(@PathParam("d1") String d1, @PathParam("d2") String d2,
            @PathParam("unidade") String unidade) {
        try {
            Map<String, Object> resultado = tissContaService.validarPeriodo(d1, d2, unidade);
            return Response.ok(resultado).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    public Response salvarConta(TissContaDto dto) {
        try {
            TissConta conta = tissContaService.salvarConta(dto);
            return Response.status(Response.Status.CREATED).entity(conta).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/itens")
    public Response salvarItem(@PathParam("id") int id, TissContaItemDto dto) {
        try {
            TissContaItem item = tissContaService.salvarItem(id, dto);
            return Response.status(Response.Status.CREATED).entity(item).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/validar")
    public Response validarConta(@PathParam("id") int id) {
        try {
            TissConta conta = tissContaService.validarConta(id);
            return Response.ok(conta).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
