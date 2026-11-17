package br.com.iv.controller;
import java.time.LocalDate;
import java.util.*; import com.fasterxml.jackson.databind.ObjectMapper; import br.com.iv.dto.DocumentoAssistencialDto; import br.com.iv.dto.CancelamentoDocumentoAssistencialDto; import br.com.iv.entity.DocumentoAssistencial; import br.com.iv.entity.EvidenciaAssistencial; import br.com.iv.security.AuthenticatedUser; import br.com.iv.service.EvidenciaAssistencialService; import jakarta.inject.Inject; import jakarta.transaction.Transactional; import jakarta.ws.rs.*; import jakarta.ws.rs.core.*; import jakarta.ws.rs.container.ContainerRequestContext;
@Path("documentos-assistenciais") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class DocumentoAssistencialController {
 @Inject ObjectMapper mapper; @Inject EvidenciaAssistencialService evidenciaAssistencialService; @Context ContainerRequestContext context;
 private AuthenticatedUser user(){Object u=context.getProperty("ghbam.user"); if(!(u instanceof AuthenticatedUser a)||!a.hasAnyPermission(List.of("kenfermagem","kmedico","kadm","pdocumentosassistenciais"))) throw new ForbiddenException(); return a;}
 @GET @Path("paciente/{id}") public List<DocumentoAssistencial> listar(@PathParam("id") int id){user();return DocumentoAssistencial.list("idpaciente=?1 order by criadoEm desc",id);}

 // Listagem geral (todos os pacientes) com filtros de unidade, status, tipo e periodo.
 // Usada pela tela de acompanhamento de rascunhos/cancelados em Documentos Assistenciais.
 @GET
 @br.com.iv.security.UnidadePadraoSessao // sem unidade usa a da sessao
 public List<DocumentoAssistencial> listarGeral(
   @QueryParam("unidade") String unidade,
   @QueryParam("status") String status,
   @QueryParam("tipo") String tipo,
   @QueryParam("nome") String nome,
   @QueryParam("inicio") String inicio,
   @QueryParam("fim") String fim,
   @QueryParam("limite") Integer limite) {
  var u = user();
  StringBuilder jpql = new StringBuilder("1=1");
  Map<String, Object> params = new HashMap<>();

  String filtroUnidade = (unidade == null || unidade.isBlank()) ? u.unidade() : unidade;
  if (filtroUnidade != null && !filtroUnidade.isBlank() && !"TODAS".equalsIgnoreCase(filtroUnidade)) {
   jpql.append(" and unidade = :unidade");
   params.put("unidade", filtroUnidade.split(":")[0].trim());
  }
  if (status != null && !status.isBlank() && !"TODOS".equalsIgnoreCase(status)) {
   List<String> statusList = Arrays.stream(status.split(","))
     .map(String::trim).filter(s -> !s.isBlank()).map(String::toUpperCase).toList();
   if (!statusList.isEmpty()) {
    jpql.append(" and status in :statuses");
    params.put("statuses", statusList);
   }
  }
  if (tipo != null && !tipo.isBlank() && !"TODOS".equalsIgnoreCase(tipo)) {
   jpql.append(" and tipo = :tipo");
   params.put("tipo", tipo);
  }
  if (nome != null && !nome.isBlank()) {
   jpql.append(" and upper(nomepaciente) like upper(:nome)");
   params.put("nome", "%" + nome.trim() + "%");
  }
  LocalDate dataInicio = parseData(inicio);
  LocalDate dataFim = parseData(fim);
  if (dataInicio != null) {
   jpql.append(" and criadoEm >= :inicio");
   params.put("inicio", dataInicio.atStartOfDay());
  }
  if (dataFim != null) {
   jpql.append(" and criadoEm <= :fim");
   params.put("fim", dataFim.atTime(23, 59, 59));
  }
  int lim = (limite == null || limite <= 0) ? 100 : Math.min(limite, 300);
  return DocumentoAssistencial.find(jpql.toString() + " order by criadoEm desc", params).page(0, lim).list();
 }

 private LocalDate parseData(String data) {
  try {
   return (data == null || data.isBlank()) ? null : LocalDate.parse(data.substring(0, 10));
  } catch (Exception e) {
   return null;
  }
 }
 @POST @Transactional public Response salvar(DocumentoAssistencialDto d){var u=user(); if(d==null||d.idpaciente==null||d.idpaciente<1||d.tipo==null||d.titulo==null||d.titulo.isBlank()) return Response.status(400).build(); var x=new DocumentoAssistencial();x.uuid=UUID.randomUUID().toString();x.tipo=d.tipo;x.status="RASCUNHO";x.idpaciente=d.idpaciente;x.idbam=d.idbam;x.prontuario=d.prontuario;x.nomepaciente=d.nomepaciente;x.titulo=d.titulo.trim();x.conteudoJson=d.conteudo==null?"{}":d.conteudo.toString();x.unidade=u.unidade();x.criadoPor=u.nome();x.criadoPorMatricula=u.matricula();x.persist();return Response.status(201).entity(x).build();}
 @POST @Path("{uuid}/concluir") @Transactional public DocumentoAssistencial concluir(@PathParam("uuid") String uuid){var u=user();DocumentoAssistencial x=DocumentoAssistencial.find("uuid",uuid).firstResult();if(x==null)throw new NotFoundException();if("CONCLUIDO".equals(x.status)||"CANCELADO".equals(x.status))throw new ClientErrorException(409);x.status="CONCLUIDO";x.concluidoPor=u.nome();x.concluidoPorMatricula=u.matricula();x.concluidoEm=java.time.LocalDateTime.now();criarPendenciaRnds(x,u);return x;}
 private void criarPendenciaRnds(DocumentoAssistencial x, AuthenticatedUser u){EvidenciaAssistencial e=new EvidenciaAssistencial();e.tipo="PENDENCIA_ENVIO";e.status="ABERTO";e.unidade=x.unidade;e.idpaciente=x.idpaciente;e.prontuario=x.prontuario;e.nomepaciente=x.nomepaciente;e.responsavel=u.nome();e.referencia=x.titulo;e.origemModulo="DOCUMENTO_ASSISTENCIAL";e.origemId=x.uuid;e.origemDescricao=x.tipo+" - "+x.titulo;e.descricao="Documento assistencial concluido aguardando envio ou reprocessamento RNDS/FHIR.";e.usuarioRegistro=u.matricula();e.detalhesJson="{\"documento\":\""+json(x.tipo)+"\",\"operacao\":\"ENVIO\",\"statusIntegracao\":\"PENDENTE\",\"sistemaOrigem\":\"GHBAM\",\"identificadorExterno\":\""+json(x.uuid)+"\"}";evidenciaAssistencialService.criar("RNDS",e);}
 private String json(String valor){return valor==null?"":valor.replace("\\","\\\\").replace("\"","\\\"");}
 @POST @Path("{uuid}/cancelar") @Transactional public Response cancelar(@PathParam("uuid") String uuid, CancelamentoDocumentoAssistencialDto dto){var u=user();String motivo=dto==null?"":String.valueOf(dto.motivo==null?"":dto.motivo).trim();if(motivo.isBlank())return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro","Motivo do cancelamento obrigatorio")).build();if(motivo.length()>4000)return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro","Motivo do cancelamento excede o limite de 4000 caracteres")).build();DocumentoAssistencial x=DocumentoAssistencial.find("uuid",uuid).firstResult();if(x==null)throw new NotFoundException();if("CANCELADO".equals(x.status))return Response.status(Response.Status.CONFLICT).entity(Map.of("erro","Documento ja esta cancelado")).build();x.status="CANCELADO";x.motivoCancelamento=motivo;x.canceladoPor=u.nome();x.canceladoPorMatricula=u.matricula();x.canceladoEm=java.time.LocalDateTime.now();return Response.ok(x).build();}
}
