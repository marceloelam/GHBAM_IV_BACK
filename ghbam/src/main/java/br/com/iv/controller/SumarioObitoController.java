package br.com.iv.controller;

import java.util.List;
import br.com.iv.dto.SumarioObitoDto;
import br.com.iv.entity.SumarioObito;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.transaction.Transactional;

@Path("sumario-obito") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class SumarioObitoController {
    @Context ContainerRequestContext request;
    private AuthenticatedUser usuario() { Object value = request == null ? null : request.getProperty("ghbam.user"); if (!(value instanceof AuthenticatedUser user) || user.unidade() == null || user.unidade().isBlank()) throw new ForbiddenException("Unidade do usuario nao identificada."); return user; }
    @GET public List<SumarioObito> listar() { AuthenticatedUser user = usuario(); return SumarioObito.list("unidade = ?1 order by dataObito desc, id desc", user.unidade()); }
    @GET @Path("/{id}") public SumarioObito buscar(@PathParam("id") Integer id) { AuthenticatedUser user = usuario(); SumarioObito item = SumarioObito.find("id = ?1 and unidade = ?2", id, user.unidade()).firstResult(); if (item == null) throw new NotFoundException(); return item; }
    @POST @Transactional public Response criar(SumarioObitoDto dto) { SumarioObito item = copiar(new SumarioObito(), dto, usuario()); item.persist(); return Response.status(Response.Status.CREATED).entity(item).build(); }
    @PUT @Path("/{id}") @Transactional public SumarioObito atualizar(@PathParam("id") Integer id, SumarioObitoDto dto) { AuthenticatedUser user = usuario(); SumarioObito item = SumarioObito.find("id = ?1 and unidade = ?2", id, user.unidade()).firstResult(); if (item == null) throw new NotFoundException(); return copiar(item, dto, user); }
    private SumarioObito copiar(SumarioObito e, SumarioObitoDto d, AuthenticatedUser u) {
        e.setUnidade(u.unidade()); e.setIdResumoAlta(d.getIdResumoAlta()); e.setBam(d.getBam()); e.setNomePaciente(d.getNomePaciente()); e.setProntuario(d.getProntuario()); e.setDataInternacao(d.getDataInternacao()); e.setDataNascimento(d.getDataNascimento()); e.setIdade(d.getIdade()); e.setSexo(d.getSexo()); e.setDataObito(d.getDataObito()); e.setHoraObito(d.getHoraObito()); e.setNumeroDO(d.getNumeroDO()); e.setSetor(d.getSetor()); e.setPermanenciaHospitalar(d.getPermanenciaHospitalar()); e.setPeriodoMulher(d.getPeriodoMulher()); e.setCausaExterna(d.getCausaExterna()); e.setEstatura(d.getEstatura()); e.setCorIdadePresumida(d.getCorIdadePresumida()); e.setSinaisAparentes(d.getSinaisAparentes()); e.setVestuario(d.getVestuario()); e.setCausaTerminal(d.getCausaTerminal()); e.setTempoTerminal(d.getTempoTerminal()); e.setCausaIntermediaria1(d.getCausaIntermediaria1()); e.setTempoIntermediaria1(d.getTempoIntermediaria1()); e.setCausaIntermediaria2(d.getCausaIntermediaria2()); e.setTempoIntermediaria2(d.getTempoIntermediaria2()); e.setCausaBasica(d.getCausaBasica()); e.setTempoBasica(d.getTempoBasica()); e.setCondicoesContribuintes(d.getCondicoesContribuintes()); e.setAgravoNotificacao(d.getAgravoNotificacao()); e.setAgravoEspecificar(d.getAgravoEspecificar()); e.setSumarioAdmissao(d.getSumarioAdmissao()); e.setMedicoAssistente(d.getMedicoAssistente()); e.setCrmAssistente(d.getCrmAssistente()); e.setDataAssinaturaAssistente(d.getDataAssinaturaAssistente()); e.setRevisor(d.getRevisor()); e.setCrmRevisor(d.getCrmRevisor()); e.setDataAssinaturaRevisor(d.getDataAssinaturaRevisor()); return e;
    }
}
