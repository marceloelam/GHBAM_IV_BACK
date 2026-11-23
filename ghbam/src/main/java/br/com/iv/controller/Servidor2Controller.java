package br.com.iv.controller;
//YHWH é minha sabedoria. A YHWH consagro esse sistema
import java.util.List;
import java.util.Map;

import br.com.iv.dto.Servidor2Dto;
import br.com.iv.entity.Servidor2;
import br.com.iv.service.Servidor2Service;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("servidor2")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class Servidor2Controller {
	private static final Logger LOG = Logger.getLogger(Servidor2Controller.class);
	@Inject
	br.com.iv.service.ServidorService servidorService;
	@POST
	@Path("/migrar-servidores/{unidade}")
	public Response migrarServidoresParaServidor2(@PathParam("unidade") String unidade) {
		try {
			servidorService.migrarServidoresParaServidor2(unidade);
			return Response.ok("Migração concluída com sucesso!").build();
		} catch (Exception e) {
			// Log completo da exceção para diagnóstico em ambiente de desenvolvimento
			LOG.error("Falha ao migrar servidores para o cadastro v2.", e);
			String msg = "Nao foi possivel concluir a migracao de servidores.";
			return Response.serverError().entity("Erro na migração: " + msg).build();
		}
	}

	@Inject
	Servidor2Service servidor2Service;
	
	@GET
	public Response listarServidor2() {
		List<Servidor2> Servidor2 = servidor2Service.listarTodosOsServidor2es();
		return Response.ok(Servidor2).build();
	}
	
	@GET
	@Path("/nome/{xServidor2}/unidade/{unidade}")
	public Response xlistarSrvidor(@PathParam("xServidor2") String xServidor2, @PathParam("unidade") String unidade) {
		List<Servidor2> Servidor2 = servidor2Service.filtarPorServidor2(xServidor2, unidade);
		return Response.ok(Servidor2).build();
	}
	
	@GET
	@Path("/matricula2/{matricula}") 
	public void alterarSenha(@PathParam("matricula") String matricula) {
		servidor2Service.alterarSenha(matricula); 
	}

	@PATCH
	@Path("/{matricula}/senha") 
	public Response atualizarSenha(@PathParam("matricula") String matricula) {
		servidor2Service.alterarSenha(matricula); 
		return Response.status(204).build();
	}
	
	@GET
	@Path("/idser/{id}/ativo/{stat}") 
	public void alterarStatus(@PathParam("id") int id, @PathParam("stat") boolean stat) {
		servidor2Service.alterarStatus(id, stat); 
	}

	@PATCH
	@Path("/{id}/ativo/{stat}") 
	public Response atualizarStatus(@PathParam("id") int id, @PathParam("stat") boolean stat) {
		servidor2Service.alterarStatus(id, stat); 
		return Response.status(204).build();
	}
	
	@GET
	@Path("/unidade/{yunidade}")
	public Response xlistarUnidades(@PathParam("yunidade") String yunidade) {
		Servidor2Dto dto = new Servidor2Dto();
		List<Servidor2> Servidor2 = servidor2Service.filtarPorUnidadeServidor2(yunidade, dto);
		return Response.ok(Servidor2).build();
	}
	
	@GET
	@Path("/medico/{yunidade}")
	public Response xlistarMedicoUnidades(@PathParam("yunidade") String yunidade) {
		Servidor2Dto dto = new Servidor2Dto();
		List<Servidor2> Servidor2 = servidor2Service.filtarPorUnidadeMedico(yunidade, dto);
		return Response.ok(Servidor2).build();
	}
	
	@GET
	@Path("/idServidor2/{idServidor2}")
	public Response unicoServidor2PorId(@PathParam("idServidor2") int idServidor2) {
		Servidor2 Servidor2 = servidor2Service.Servidor2Id(idServidor2);
		return Response.ok(Servidor2).build();
	}
	
	
	@GET
	@Path("/matricula/{matricula}")
	public Response unicoServidor2(@PathParam("matricula") String matricula) {
		Servidor2 Servidor2 = servidor2Service.servidor2Matricula(matricula);
		return Response.ok(Servidor2).build();
	}
	
	

	@GET
	@Path("/nome/{nome}/unidade/{unidade}")
	public Response unicoServidor2(@PathParam("nome") String nome, @PathParam("unidade") String unidade) {
		Servidor2Dto dto = new Servidor2Dto();
		Servidor2 Servidor2 = servidor2Service.findByNomeDoMedico(nome,unidade, dto);
		return Response.ok(Servidor2).build();
	}
	
	
	
	@POST
	public Response salvarSetores(Servidor2Dto dto,
			@jakarta.ws.rs.HeaderParam(br.com.iv.plataforma.SuperusuarioFacialResource.CABECALHO) String comprovante,
			@jakarta.ws.rs.core.Context jakarta.ws.rs.container.ContainerRequestContext ctx) {
		// Sem matricula a linha fica orfa: o login e a tela nunca a encontram e
		// cada novo "Alterar os dados" cria outra (caso 51151, 18/11/2026).
		if (dto == null || dto.getMatricula() == null || dto.getMatricula().isBlank()) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(Map.of("erro", "Matricula nao informada. Busque a matricula novamente antes de salvar."))
				.build();
		}
		try {
			Servidor2 Servidor2 = servidor2Service.salvarServidor2(dto, usuario(ctx), comprovante);
			return Response.ok(Servidor2).status(201).build();
		} catch (br.com.iv.plataforma.SuperusuarioConcessaoService.NegadoException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", e.getMessage())).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT)
				.entity(Map.of("erro", e.getMessage() == null ? "Operacao nao permitida." : e.getMessage()))
				.build();
		}
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarSetor(@PathParam("id") int id, Servidor2Dto dto,
			@jakarta.ws.rs.HeaderParam(br.com.iv.plataforma.SuperusuarioFacialResource.CABECALHO) String comprovante,
			@jakarta.ws.rs.core.Context jakarta.ws.rs.container.ContainerRequestContext ctx) {
		try {
			servidor2Service.atualizarServidor2(id, dto, usuario(ctx), comprovante);
		} catch (br.com.iv.plataforma.SuperusuarioConcessaoService.NegadoException e) {
			return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", e.getMessage())).build();
		}
		return Response.status(204).build();
	}

	private static br.com.iv.security.AuthenticatedUser usuario(jakarta.ws.rs.container.ContainerRequestContext ctx) {
		Object u = ctx == null ? null : ctx.getProperty("ghbam.user");
		return u instanceof br.com.iv.security.AuthenticatedUser a ? a : br.com.iv.security.AuthenticatedUser.empty();
	}

	@DELETE
	@Path("/remover/{id}")
	public Response removerSetor(@PathParam("id") Long id, Servidor2Dto dto) {
		servidor2Service.removeServidor2(id);
		return Response.status(204).build();
	}
}
