package br.com.iv.controller;

import java.util.List;

import br.com.iv.dto.AlergiaTriagemDto;
import br.com.iv.dto.ProntuarioClinicoItemDto;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.service.ProntuarioClinicoItemService;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("prontuario-clinico")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProntuarioClinicoItemController {

    @Inject
    ProntuarioClinicoItemService service;

    @GET
    @Path("/alergias-ativas/{idpaciente}")
    public Response listarAlergiasAtivas(@PathParam("idpaciente") int idpaciente) {
        return Response.ok(service.listarAlergiasAtivas(idpaciente)).build();
    }

    @GET
    @Path("/paciente/{idpaciente}/unidade/{unidade}")
    public Response listar(@PathParam("idpaciente") int idpaciente, @PathParam("unidade") String unidade) {
        List<ProntuarioClinicoItem> itens = service.listar(idpaciente, unidade);
        return Response.ok(itens).build();
    }

    @GET
    @Path("/paciente/{idpaciente}/tipo/{tipo}/unidade/{unidade}")
    public Response listarPorTipo(
            @PathParam("idpaciente") int idpaciente,
            @PathParam("tipo") String tipo,
            @PathParam("unidade") String unidade) {
        List<ProntuarioClinicoItem> itens = service.listarPorTipo(idpaciente, tipo, unidade);
        return Response.ok(itens).build();
    }

    @POST
    public Response salvar(ProntuarioClinicoItemDto dto) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Paciente obrigatorio\"}")
                    .build();
        }
        ProntuarioClinicoItem item = service.salvar(dto);
        return Response.status(Response.Status.CREATED).entity(item).build();
    }

    /**
     * Promove para o prontuario do paciente a alergia digitada na triagem.
     * Chamado ao salvar a triagem; a alergia deixa de valer so para aquele BAM.
     */
    @POST
    @Path("/alergia-triagem")
    public Response registrarAlergiaDaTriagem(AlergiaTriagemDto dto, @Context ContainerRequestContext context) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Paciente obrigatorio\"}")
                    .build();
        }
        preencherUsuarioAutenticado(dto, context);
        try {
            List<ProntuarioClinicoItem> criados = service.registrarAlergiasDaTriagem(dto);
            return Response.status(criados.isEmpty() ? Response.Status.OK : Response.Status.CREATED)
                    .entity(criados)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") int id, ProntuarioClinicoItemDto dto) {
        ProntuarioClinicoItem item = service.atualizar(id, dto);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(item).build();
    }

    @PUT
    @Path("/{id}/inativar")
    public Response inativar(@PathParam("id") int id, ProntuarioClinicoItemDto dto) {
        boolean ok = service.inativar(id, dto == null ? new ProntuarioClinicoItemDto() : dto);
        return ok ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private void preencherUsuarioAutenticado(AlergiaTriagemDto dto, ContainerRequestContext context) {
        if (dto == null || context == null) {
            return;
        }
        Object value = context.getProperty("ghbam.user");
        if (!(value instanceof AuthenticatedUser user)) {
            return;
        }
        if (dto.getCriadoPorMatricula() == null || dto.getCriadoPorMatricula().isBlank()) {
            dto.setCriadoPorMatricula(user.matricula());
        }
        if (dto.getCriadoPor() == null || dto.getCriadoPor().isBlank()) {
            dto.setCriadoPor(user.nome());
        }
        if (dto.getUnidade() == null || dto.getUnidade().isBlank()) {
            dto.setUnidade(user.unidade());
        }
    }
}
