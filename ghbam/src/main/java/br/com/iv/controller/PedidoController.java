package br.com.iv.controller;


import java.net.URI;
import java.util.List;

import br.com.iv.dto.PedidoDTO;
import br.com.iv.entity.Pedido;
import br.com.iv.dto.PedidoItensDto;
import br.com.iv.service.PedidoItemService;
import br.com.iv.service.PedidoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/pedidos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PedidoController {

    @Inject
    PedidoService service;
    
    @Inject
    PedidoItemService serviceItem;

    @POST
    public Response create(PedidoDTO dto) {
        Pedido created = service.create(dto);
        // Retornar 201 com o objeto criado
        return Response.created(URI.create("/api/pedidos/" + created.getId())).entity(created).build();
    }

    @GET
    @Path("/unidade/{unidade}/data/{data}")
    public Response listAll(@PathParam("unidade") String unidade, @PathParam("data") String data) {
        return Response.ok(service.findByUnidade(unidade, data)).build();
    }

    @GET
    @Path("/id/{id}")
    public Response getById(@PathParam("id") Long id) {
        Pedido p = service.findById(id);
        if (p == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(p).build();
    }
    
    @GET
    @Path("/iditem/{id}")
    public Response getByIdItem(@PathParam("id") int id) {
    	List<PedidoItensDto>  p = service.findByIdItem(id);
    	return Response.ok(p).build();
    }

    @GET
    @Path("/iditemrecebido/{id}")
    public void itemRecebido(@PathParam("id") int id) {
    	marcarItemRecebido(id);
    }

    @PATCH
    @Path("/itens/{id}/recebimento")
    public void itemRecebidoPatch(@PathParam("id") int id) {
    	marcarItemRecebido(id);
    }

    private void marcarItemRecebido(int id) {
    	service.itemRecebido(id);
    }
    
    
    
    @DELETE
    @Path("/deletar/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
    
    @DELETE
    @Path("/deletaritem/{id}")
    public Response deleteItem(@PathParam("id") Long id) {
    	serviceItem.delete(id);
        return Response.noContent().build();
    }
    
    
}
