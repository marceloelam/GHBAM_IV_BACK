package br.com.iv.controller;

import java.time.LocalDate;
import java.util.*;
import br.com.iv.dto.GedDocumentoDto;
import br.com.iv.dto.GedDocumentoResumoDto;
import br.com.iv.dto.GedRemocaoDto;
import br.com.iv.entity.GedDocumento;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.ArmazenamentoArquivoService;
import br.com.iv.service.GedArquivoRemovidoService;
import br.com.iv.service.GedReenvioArmazenamentoService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("ged/documentos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GedDocumentoController {
 @Context ContainerRequestContext context;

 @Inject EntityManager entityManager;
 @Inject SecurityPermissionService securityPermissionService;
 @Inject ArmazenamentoArquivoService armazenamento;
 @Inject GedArquivoRemovidoService arquivoRemovido;

 private static final Logger LOG = Logger.getLogger(GedDocumentoController.class);

 @ConfigProperty(name = "ghbam.ged.tamanho-maximo-bytes", defaultValue = "15728640") long tamanhoMaximoBytes;
 @ConfigProperty(name = "ghbam.ged.mimes-permitidos", defaultValue = "application/pdf,image/jpeg,image/png,image/webp") String mimesPermitidos;

 // Cada acao do GED depende da sua propria permissao do cadastro: perfil
 // (kadm, kmedico, kenfermagem) ou acesso vizinho (pnir, pdocumentosassistenciais)
 // nao liberam mais o modulo por tabela. Superusuario continua passando por
 // AuthenticatedUser.hasPermission.
 private static final List<String> PERMISSOES_ESCRITA=List.of("pged");
 // Consultar e abrir o anexo exige pgedconsulta; quem pode anexar (pged) tambem le.
 private static final List<String> PERMISSOES_LEITURA=List.of("pgedconsulta","pged");

 // Remover exige permissao propria (servidor2.gedremover): quem anexa nao
 // necessariamente pode apagar o anexo de outro.
 private static final List<String> PERMISSOES_REMOCAO=List.of("pgedremover");

 private AuthenticatedUser user(){return exigir(PERMISSOES_ESCRITA);}

 private AuthenticatedUser userRemocao(){return exigir(PERMISSOES_REMOCAO);}

 private AuthenticatedUser userLeitura(){return exigir(PERMISSOES_LEITURA);}

 private AuthenticatedUser exigir(List<String> permissoes){
  Object u=context.getProperty("ghbam.user");
  if(!(u instanceof AuthenticatedUser a)||!a.hasAnyPermission(permissoes)) throw new ForbiddenException();
  return a;
 }

 @GET
 public List<GedDocumentoResumoDto> listar(@QueryParam("paciente") String paciente,@QueryParam("idpaciente") Integer idpaciente,@QueryParam("idbam") Integer idbam,@QueryParam("prontuario") String prontuario,@QueryParam("tipo") String tipo,@QueryParam("unidade") String unidade,@QueryParam("inicio") String inicio,@QueryParam("fim") String fim,@QueryParam("limite") Integer limite){
  var u=userLeitura();
  StringBuilder jpql=new StringBuilder("status <> 'REMOVIDO'");
  Map<String,Object> params=new HashMap<>();
  String filtroUnidade=(unidade==null||unidade.isBlank())?u.unidade():unidade;
  if(filtroUnidade!=null&&!filtroUnidade.isBlank()&&!"TODAS".equalsIgnoreCase(filtroUnidade)){jpql.append(" and unidade = :unidade");params.put("unidade",filtroUnidade.split(":")[0].trim());}
  if(idpaciente!=null&&idpaciente>0){jpql.append(" and idpaciente = :idpaciente");params.put("idpaciente",idpaciente);}
  if(idbam!=null&&idbam>0){jpql.append(" and idbam = :idbam");params.put("idbam",idbam);}
  if(prontuario!=null&&!prontuario.isBlank()){jpql.append(" and prontuario = :prontuario");params.put("prontuario",prontuario.trim());}
  if(tipo!=null&&!tipo.isBlank()&&!"TODOS".equalsIgnoreCase(tipo)){jpql.append(" and tipo = :tipo");params.put("tipo",tipo.trim());}
  if(paciente!=null&&!paciente.isBlank()){jpql.append(" and upper(nomePaciente) like upper(:paciente)");params.put("paciente","%"+paciente.trim()+"%");}
  LocalDate dataInicio=parseData(inicio),dataFim=parseData(fim);
  if(dataInicio!=null){jpql.append(" and criadoEm >= :inicio");params.put("inicio",dataInicio.atStartOfDay());}
  if(dataFim!=null){jpql.append(" and criadoEm <= :fim");params.put("fim",dataFim.atTime(23,59,59));}
  int lim=(limite==null||limite<=0)?100:Math.min(limite,300);
  return GedDocumento.find(jpql+" order by criadoEm desc",params).project(GedDocumentoResumoDto.class).page(0,lim).list();
 }

 @POST
 @Transactional
 public Response salvar(GedDocumentoDto d){
  var u=user();
  if(d==null) return erro("Documento obrigatorio");
  String base64=limpar(d.conteudoBase64),nome=limpar(d.nomeArquivo),mime=limpar(d.tipoMime),tipo=limpar(d.tipo);
  if(base64.isBlank()) return erro("Conteudo Base64 obrigatorio");
  if(nome.isBlank()) return erro("Nome do arquivo obrigatorio");
  if(mime.isBlank()) return erro("Tipo MIME obrigatorio");
  if(tipo.isBlank()) return erro("Tipo do documento obrigatorio");
  if((d.idpaciente==null||d.idpaciente<1)&&(d.idbam==null||d.idbam<1)&&limpar(d.prontuario).isBlank()) return erro("Informe paciente, BAM ou prontuario");
  if(!mimePermitido(mime)) return erro("Tipo de arquivo nao permitido. Aceitos: "+mimesPermitidos);
  byte[] conteudo;
  try{conteudo=Base64.getMimeDecoder().decode(base64);}catch(IllegalArgumentException e){return erro("Conteudo Base64 invalido");}
  if(conteudo.length==0) return erro("Arquivo vazio");
  if(conteudo.length>tamanhoMaximoBytes) return erro("Arquivo acima do limite de "+(tamanhoMaximoBytes/1048576)+" MB");
  var x=new GedDocumento();
  x.uuid=UUID.randomUUID().toString();x.tipo=tipo;x.status="ATIVO";x.idpaciente=d.idpaciente;x.idbam=d.idbam;x.prontuario=limpar(d.prontuario);
  x.nomePaciente=!limpar(d.nomePaciente).isBlank()?limpar(d.nomePaciente):limpar(d.nomepaciente);
  x.unidade=!limpar(d.unidade).isBlank()?limpar(d.unidade):u.unidade();x.nomeArquivo=nome;x.tipoMime=mime;x.tamanhoBytes=(long)conteudo.length;
  x.descricao=limpar(d.descricao);x.conteudoBase64=base64;x.criadoPor=u.nome();x.criadoPorMatricula=u.matricula();
  // Com o MinIO ligado o arquivo sai do banco. Se o MinIO falhar, grava no banco
  // como antes: o anexo do paciente nao pode se perder por indisponibilidade.
  if(armazenamento.habilitado()){
   try{x.armazenamentoChave=armazenamento.salvar(GedReenvioArmazenamentoService.chave(x),conteudo,mime);x.conteudoBase64=null;}
   catch(RuntimeException e){LOG.errorf("GED: MinIO indisponivel, anexo %s gravado no banco (reenvio automatico em ate 10 min): %s",x.uuid,e.getMessage());}
  }
  x.persist();
  return Response.status(Response.Status.CREATED).entity(resumo(x)).build();
 }

 @POST
 @Path("{id}/remover")
 @Transactional
 public Response remover(@PathParam("id") String id,GedRemocaoDto dto){
  var u=userRemocao();
  String motivo=limpar(dto==null?null:dto.motivo);
  String senha=dto==null||dto.senha==null?"":dto.senha;
  if(motivo.isBlank()) return erro("Motivo da remocao obrigatorio");
  if(senha.isBlank()) return erro("Informe a sua senha para confirmar a remocao");
  // Reautenticacao: a sessao aberta nao basta, quem remove confirma a propria senha.
  if(securityPermissionService.authenticate(u.matricula(),senha)==null){
   LOG.warnf("GED: senha incorreta ao tentar remover o documento %s por %s (%s), unidade %s",id,u.nome(),u.matricula(),u.unidade());
   return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro","Senha invalida. A remocao nao foi realizada.")).build();
  }
  GedDocumento x=buscar(id);
  if(x==null||!daUnidadeDoUsuario(x,u)) throw new NotFoundException();
  if("REMOVIDO".equals(x.status)) return Response.status(Response.Status.CONFLICT).entity(Map.of("erro","Documento ja esta removido")).build();
  x.status="REMOVIDO";x.motivoRemocao=motivo;x.removidoPor=u.nome();x.removidoPorMatricula=u.matricula();x.removidoEm=java.time.LocalDateTime.now();
  arquivoRemovido.arquivar(x);
  LOG.infof("GED: documento %s (%s) do paciente %s / prontuario %s removido por %s (%s) na unidade %s. Motivo: %s",
    x.id,x.nomeArquivo,x.nomePaciente,x.prontuario,u.nome(),u.matricula(),x.unidade,motivo);
  return Response.ok(resumo(x)).build();
 }

 @GET
 @Path("{id}/conteudo")
 @Produces(MediaType.APPLICATION_OCTET_STREAM)
 public Response conteudo(@PathParam("id") String id){
  var u=userLeitura();
  GedDocumento x=buscar(id);
  if(x==null||"REMOVIDO".equals(x.status)||!daUnidadeDoUsuario(x,u)) throw new NotFoundException();
  byte[] bytes;
  if(!limpar(x.armazenamentoChave).isBlank()){
   try{bytes=armazenamento.ler(x.armazenamentoChave);}
   catch(RuntimeException e){LOG.errorf("GED: falha ao ler anexo %s do MinIO: %s",x.uuid,e.getMessage());throw new ServiceUnavailableException("Arquivo temporariamente indisponivel");}
  }else{
   try{bytes=Base64.getMimeDecoder().decode(limpar(x.conteudoBase64));}catch(IllegalArgumentException e){throw new InternalServerErrorException("Conteudo armazenado invalido");}
  }
  String nome=limpar(x.nomeArquivo).isBlank()?("documento-ged-"+x.id):x.nomeArquivo;
  return Response.ok(bytes)
    .type(limpar(x.tipoMime).isBlank()?MediaType.APPLICATION_OCTET_STREAM:x.tipoMime)
    .header("Content-Disposition","attachment; filename=\""+nome.replace("\"","")+"\"")
    .header("Content-Length",bytes.length)
    .build();
 }

 /**
  * Pacientes que ja possuem documento ativo no GED, para o filtro da consulta.
  * Evita que o operador procure por quem nunca teve anexo.
  */
 @GET
 @Path("pacientes-com-documento")
 public List<Map<String,Object>> pacientesComDocumento(@QueryParam("termo") String termo,@QueryParam("unidade") String unidade){
  var u=userLeitura();
  StringBuilder jpql=new StringBuilder("select g.idpaciente, g.nomePaciente, g.prontuario, max(g.idbam) from GedDocumento g where g.status <> 'REMOVIDO' and g.nomePaciente is not null");
  Map<String,Object> params=new HashMap<>();
  String filtroUnidade=(unidade==null||unidade.isBlank())?u.unidade():unidade;
  if(filtroUnidade!=null&&!filtroUnidade.isBlank()&&!"TODAS".equalsIgnoreCase(filtroUnidade)){jpql.append(" and g.unidade = :unidade");params.put("unidade",filtroUnidade.split(":")[0].trim());}
  String busca=limpar(termo);
  if(!busca.isBlank()){jpql.append(" and upper(g.nomePaciente) like upper(:termo)");params.put("termo","%"+busca+"%");}
  jpql.append(" group by g.idpaciente, g.nomePaciente, g.prontuario order by g.nomePaciente");
  var query=entityManager.createQuery(jpql.toString(),Object[].class).setMaxResults(30);
  params.forEach(query::setParameter);
  List<Map<String,Object>> resposta=new ArrayList<>();
  for(Object[] linha:query.getResultList()){
   Map<String,Object> item=new LinkedHashMap<>();
   item.put("idpaciente",linha[0]);
   item.put("nomePaciente",linha[1]);
   item.put("prontuario",linha[2]);
   item.put("idbam",linha[3]);
   resposta.add(item);
  }
  return resposta;
 }

 /**
  * Ultimo BAM de cada paciente, em lote. A busca de pacientes por nome
  * (pacientes/{nome}) devolve so o cadastro, sem atendimento, e a lista de
  * sugestoes do GED precisa mostrar o BAM sem uma requisicao por paciente.
  */
 @GET
 @Path("pacientes-ultimo-bam")
 public Map<Integer,Integer> ultimoBamPorPaciente(@QueryParam("ids") String ids){
  userLeitura();
  List<Integer> lista=new ArrayList<>();
  for(String parte:limpar(ids).split(",")){
   try{int v=Integer.parseInt(parte.trim());if(v>0&&!lista.contains(v))lista.add(v);}catch(NumberFormatException ignored){}
   if(lista.size()>=50) break;
  }
  Map<Integer,Integer> resultado=new LinkedHashMap<>();
  if(lista.isEmpty()) return resultado;
  // idbam e sequencial, entao o maior id do paciente e o atendimento mais recente.
  List<Object[]> linhas=entityManager
    .createQuery("select b.paciente.idpaciente, max(b.idbam) from Bam b where b.paciente.idpaciente in :ids group by b.paciente.idpaciente",Object[].class)
    .setParameter("ids",lista).getResultList();
  for(Object[] linha:linhas){
   if(linha[0]==null||linha[1]==null) continue;
   resultado.put(((Number)linha[0]).intValue(),((Number)linha[1]).intValue());
  }
  return resultado;
 }

 /**
  * Documento do GED pertence a unidade onde foi anexado. Quem esta logado em
  * uma unidade nao abre nem remove anexo de outra; "TODAS" (perfil global) ve
  * tudo, mesmo criterio ja aplicado na listagem.
  */
 private boolean daUnidadeDoUsuario(GedDocumento x,AuthenticatedUser u){
  String doUsuario=normalizarUnidade(u.unidade());
  if(doUsuario.isBlank()||"TODAS".equalsIgnoreCase(doUsuario)) return true;
  String doDocumento=normalizarUnidade(x.unidade);
  return doDocumento.isBlank()||doDocumento.equalsIgnoreCase(doUsuario);
 }

 private String normalizarUnidade(String unidade){
  String valor=limpar(unidade);
  return valor.isBlank()?"":valor.split(":")[0].trim();
 }

 private boolean mimePermitido(String mime){
  for(String permitido:mimesPermitidos.split(",")) if(permitido.trim().equalsIgnoreCase(mime)) return true;
  return false;
 }

 private GedDocumentoResumoDto resumo(GedDocumento x){
  return new GedDocumentoResumoDto(x.id,x.uuid,x.tipo,x.status,x.idpaciente,x.idbam,x.prontuario,x.nomePaciente,x.unidade,
    x.nomeArquivo,x.tipoMime,x.tamanhoBytes,x.descricao,x.criadoPor,x.criadoPorMatricula,x.criadoEm,x.atualizadoEm);
 }

 private GedDocumento buscar(String id){
  if(id==null||id.isBlank()) return null;
  try{return GedDocumento.findById(Integer.valueOf(id));}catch(Exception ignored){return GedDocumento.find("uuid",id).firstResult();}
 }
 private Response erro(String mensagem){return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro",mensagem)).build();}
 private String limpar(String v){return String.valueOf(v==null?"":v).trim();}
 private LocalDate parseData(String data){try{return(data==null||data.isBlank())?null:LocalDate.parse(data.substring(0,10));}catch(Exception e){return null;}}
}
