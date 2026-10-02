package br.com.iv.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


import br.com.iv.dto.AnamneseInternacaoDto;
import br.com.iv.entity.AnamneseInternacao;
import br.com.iv.service.AnamneseInternacaoService;

@Path("anamneseinternacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AnamneseInternacaoController {

	@Inject
	AnamneseInternacaoService anamneseInternacaoService;
	
	@GET
	@Path("/nbam/{nbam}")
	public AnamneseInternacao xlistarAnamnesePeloBam(@PathParam("nbam") int nbam) {
		return anamneseInternacaoService.filtarPorUnicoAnamneseInternacaoPeloBam(nbam);
		
	}
	
	@GET
	@Path("/idanamnese/{idanamnese}")
	public AnamneseInternacao xlistarAnamnese(@PathParam("idanamnese") int idanamnese) {
		return anamneseInternacaoService.filtarPorUnicoAnamneseInternacaoPeloBam(idanamnese);
		
	}
	
	@POST
	public Response salvarAnamneseInternacao(AnamneseInternacaoDto dto) {
		AnamneseInternacao anamnese = anamneseInternacaoService.salvarAnamnese(dto);
		return Response.ok(anamnese).status(201).build();
	}
	
	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarAnamneseInternacao(@PathParam("id") int id, AnamneseInternacaoDto dto) {
		anamneseInternacaoService.atualizarAnamneseInternacao(id, dto);
		return Response.status(204).build();
	}
	
	
}
