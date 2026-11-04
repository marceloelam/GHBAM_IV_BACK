package br.com.iv.resource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import br.com.iv.dto.PacientesDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.service.BamService;
import br.com.iv.service.PacientesService;
import br.com.iv.service.PrescricaoItemEstruturadoService;
import br.com.iv.service.ProntuarioClinicoItemService;
import br.com.iv.dto.PharmaciaConfirmacaoRequest;
import br.com.iv.service.PharmaciaConfirmacaoService;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.container.ContainerRequestContext;
import br.com.iv.service.PharmaciaRequestScope;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaDispensacao;
import br.com.iv.service.PharmaciaDispensacaoService;

@Path("/pharmacia/dispensacao")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaDispensacaoResource {

    @Inject
    PharmaciaDispensacaoService service;

    @Inject PharmaciaRequestScope scope;
    @Inject PharmaciaConfirmacaoService confirmacao;
    @Context ContainerRequestContext request;
    @Inject PacientesService pacientes;
    @Inject ProntuarioClinicoItemService prontuarioClinico;
    @Inject BamService bams;
    @Inject PrescricaoItemEstruturadoService prescricaoItens;

    @GET
    public List<PharmaciaDispensacao> listAll() {
        String unidade = scope.unidadeAtual(request);
        scope.exigirUnidade(request, unidade);
        return service.findByUnidade(unidade);
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaDispensacao d = service.findById(id);
        if (d == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        scope.exigirUnidade(request, d.getUnidadeSigla());
        return Response.ok(d).build();
    }

    @GET
    @Path("/paciente/{pacienteId}")
    public List<PharmaciaDispensacao> findByPaciente(@PathParam("pacienteId") Long pacienteId) {
        String unidade = scope.unidadeAtual(request);
        scope.exigirUnidade(request, unidade);
        return service.findByPacienteEUnidade(pacienteId, unidade);
    }

    // Leituras do Atendimento a Paciente: so o que o balcao usa, sem abrir a farmacia
    // as rotas gerais de paciente, BAM e prontuario.
    @GET
    @Path("/pacientes/{nome}")
    public List<Map<String, Object>> buscarPacientes(@PathParam("nome") String nome) {
        exigirDispensacao();
        if (nome == null || nome.trim().length() < 2) return List.of();
        return pacientes.xlocalizarPaciente(nome, new PacientesDto()).stream()
                .map(this::identificacao)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/paciente/{pacienteId}/alergias")
    public List<String> listarAlergias(@PathParam("pacienteId") int pacienteId) {
        exigirDispensacao();
        return prontuarioClinico.listarAlergiasAtivas(pacienteId);
    }

    @GET
    @Path("/paciente/{pacienteId}/prescricao")
    public Map<String, Object> listarPrescricao(@PathParam("pacienteId") int pacienteId) {
        String unidade = exigirDispensacao();
        Map<String, Object> resposta = new LinkedHashMap<>();
        Bam bam = bams.filtrarPorIdDoPcienteUltimoBam(pacienteId, unidade);
        resposta.put("idbam", bam == null ? null : bam.getIdbam());
        resposta.put("itens", bam == null ? List.of() : prescricaoItens.listarPorBam(bam.getIdbam(), true).stream()
                .filter(item -> "MEDICAMENTO".equalsIgnoreCase(item.getTipoItem()))
                .collect(Collectors.toList()));
        return resposta;
    }

    private String exigirDispensacao() {
        String unidade = scope.unidadeAtual(request);
        scope.exigirArea(request, unidade, PharmaciaRequestScope.DISPENSACAO);
        return unidade;
    }

    private Map<String, Object> identificacao(Pacientes paciente) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("idpaciente", paciente.getIdpaciente());
        dados.put("nome", paciente.getNome());
        dados.put("nascimento", paciente.getNascimento());
        dados.put("idade", paciente.getIdade());
        dados.put("mae", paciente.getMae());
        dados.put("cpf", paciente.getCpf());
        dados.put("sexo", paciente.getSexo());
        return dados;
    }

    @POST
    public Response save(PharmaciaDispensacao dispensacao) {
        if (dispensacao == null) throw new BadRequestException("Dispensacao obrigatoria.");
        dispensacao.setUsuarioMatricula(scope.exigirArea(request, dispensacao.getUnidadeSigla(), PharmaciaRequestScope.DISPENSACAO).matricula());
        try {
            PharmaciaConfirmacaoRequest req = new PharmaciaConfirmacaoRequest();
            req.itens = List.of(dispensacao);
            req.chaveConfirmacao = java.util.UUID.randomUUID().toString();
            PharmaciaDispensacao saved = confirmacao.confirmar(req).get(0);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/confirmar")
    public Response confirmar(PharmaciaConfirmacaoRequest req) {
        if (req == null || req.itens == null || req.itens.isEmpty()) throw new BadRequestException("Itens obrigatorios.");
        if (req.chaveConfirmacao == null || req.chaveConfirmacao.isBlank()) throw new BadRequestException("Chave de confirmacao obrigatoria.");
        for (PharmaciaDispensacao item : req.itens) {
            if (item == null) throw new BadRequestException("Item invalido.");
            item.setUsuarioMatricula(scope.exigirArea(request, item.getUnidadeSigla(), PharmaciaRequestScope.DISPENSACAO).matricula());
        }
        try {
            return Response.status(Response.Status.CREATED).entity(confirmacao.confirmar(req)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/lote")
    public Response saveBatch(List<PharmaciaDispensacao> itens) {
        PharmaciaConfirmacaoRequest req = new PharmaciaConfirmacaoRequest();
        req.itens = itens;
        return confirmar(req);
    }

    @POST
    @Path("/devolver/{id}")
    public Response devolver(@PathParam("id") Long id, Map<String, Object> body) {
        PharmaciaDispensacao original = service.findById(id);
        if (original == null) throw new NotFoundException();
        String matricula = scope.exigirArea(request, original.getUnidadeSigla(), PharmaciaRequestScope.DISPENSACAO).matricula();
        if (body == null) throw new BadRequestException("Dados obrigatorios.");
        try {
            Long loteId = body.get("loteId") != null ? Long.valueOf(body.get("loteId").toString()) : null;
            Integer quantidade = body.get("quantidade") != null ? Integer.valueOf(body.get("quantidade").toString()) : null;
            String unidadeSigla = body.get("unidadeSigla") != null ? body.get("unidadeSigla").toString() : null;
            service.devolver(id, loteId, quantidade, unidadeSigla, matricula);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        try {
            service.delete(id);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
