package br.com.iv.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import br.com.iv.dto.CautelaCancelamentoDto;
import br.com.iv.dto.CautelaDevolucaoDto;
import br.com.iv.dto.CautelaDto;
import br.com.iv.entity.Cautela;
import br.com.iv.security.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;

/**
 * Cautela: item entregue sob responsabilidade, com obrigacao de devolucao.
 *
 * Acesso depende so de "pcautela" (bloco NIR do cadastro de servidor2). Perfil
 * vizinho (pnir, kadm) nao libera por tabela - mesmo criterio ja aplicado no GED,
 * onde atalho de perfil vazava o modulo para quem nao tinha o toggle.
 */
@Path("cautela")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CautelaController {
 @Context ContainerRequestContext context;

 @Inject EntityManager entityManager;

 private static final Logger LOG = Logger.getLogger(CautelaController.class);

 private static final List<String> PERMISSOES = List.of("pcautela");

 private static final List<String> CATEGORIAS = List.of(
   "PATRIMONIO", "EQUIPAMENTO", "PRONTUARIO", "INSTRUMENTAL", "OPME", "CHAVE", "EPI", "OUTROS");
 private static final List<String> ESTADOS_DEVOLUCAO = List.of("OK", "AVARIADO", "EXTRAVIADO");

 private AuthenticatedUser user(){
  Object u = context.getProperty("ghbam.user");
  if(!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(PERMISSOES)) throw new ForbiddenException();
  return a;
 }

 @GET
 public List<Cautela> listar(@QueryParam("situacao") String situacao,
                             @QueryParam("categoria") String categoria,
                             @QueryParam("responsavel") String responsavel,
                             @QueryParam("item") String item,
                             @QueryParam("setor") String setor,
                             @QueryParam("unidade") String unidade,
                             @QueryParam("vencidas") Boolean vencidas,
                             @QueryParam("inicio") String inicio,
                             @QueryParam("fim") String fim,
                             @QueryParam("limite") Integer limite){
  var u = user();
  StringBuilder jpql = new StringBuilder("1 = 1");
  Map<String,Object> params = new HashMap<>();

  String filtroUnidade = normalizarUnidade((unidade==null||unidade.isBlank()) ? u.unidade() : unidade);
  if(!filtroUnidade.isBlank() && !"TODAS".equalsIgnoreCase(filtroUnidade)){
   jpql.append(" and unidade = :unidade"); params.put("unidade", filtroUnidade);
  }
  if(situacao!=null && !situacao.isBlank() && !"TODAS".equalsIgnoreCase(situacao)){
   jpql.append(" and situacao = :situacao"); params.put("situacao", situacao.trim().toUpperCase(Locale.ROOT));
  }
  if(categoria!=null && !categoria.isBlank() && !"TODAS".equalsIgnoreCase(categoria)){
   jpql.append(" and categoria = :categoria"); params.put("categoria", categoria.trim().toUpperCase(Locale.ROOT));
  }
  if(responsavel!=null && !responsavel.isBlank()){
   jpql.append(" and (upper(responsavelNome) like upper(:responsavel) or responsavelMatricula = :matricula)");
   params.put("responsavel", "%"+responsavel.trim()+"%");
   params.put("matricula", responsavel.trim());
  }
  if(item!=null && !item.isBlank()){
   jpql.append(" and (upper(item) like upper(:item) or upper(coalesce(identificador,'')) like upper(:item))");
   params.put("item", "%"+item.trim()+"%");
  }
  if(setor!=null && !setor.isBlank()){
   jpql.append(" and upper(coalesce(setor,'')) like upper(:setor)"); params.put("setor", "%"+setor.trim()+"%");
  }
  // Vencidas: so faz sentido sobre o que ainda esta fora. Prazo vazio nunca vence.
  if(Boolean.TRUE.equals(vencidas)){
   jpql.append(" and situacao = 'EM_CAUTELA' and dataPrevista is not null and dataPrevista < :agora");
   params.put("agora", LocalDateTime.now());
  }
  LocalDate dataInicio = parseData(inicio), dataFim = parseData(fim);
  if(dataInicio!=null){ jpql.append(" and dataSaida >= :inicio"); params.put("inicio", dataInicio.atStartOfDay()); }
  if(dataFim!=null){ jpql.append(" and dataSaida <= :fim"); params.put("fim", dataFim.atTime(23,59,59)); }

  int lim = (limite==null||limite<=0) ? 100 : Math.min(limite, 500);
  return Cautela.find(jpql+" order by dataSaida desc", params).page(0, lim).list();
 }

 /**
  * Contadores do topo da tela. O operador precisa ver "quantas estao vencidas"
  * antes de filtrar, senao o modulo vira so um cadastro sem cobranca.
  */
 @GET
 @Path("resumo")
 public Map<String,Long> resumo(@QueryParam("unidade") String unidade){
  var u = user();
  String filtroUnidade = normalizarUnidade((unidade==null||unidade.isBlank()) ? u.unidade() : unidade);
  boolean todas = filtroUnidade.isBlank() || "TODAS".equalsIgnoreCase(filtroUnidade);
  LocalDateTime agora = LocalDateTime.now();

  Map<String,Long> r = new LinkedHashMap<>();
  r.put("emCautela", contar("situacao = 'EM_CAUTELA'", todas, filtroUnidade));
  r.put("devolvidas", contar("situacao = 'DEVOLVIDO'", todas, filtroUnidade));
  r.put("canceladas", contar("situacao = 'CANCELADO'", todas, filtroUnidade));
  r.put("vencidas", todas
    ? Cautela.count("situacao = 'EM_CAUTELA' and dataPrevista is not null and dataPrevista < ?1", agora)
    : Cautela.count("situacao = 'EM_CAUTELA' and dataPrevista is not null and dataPrevista < ?1 and unidade = ?2", agora, filtroUnidade));
  return r;
 }

 private long contar(String query, boolean todas, String unidade){
  return todas ? Cautela.count(query) : Cautela.count(query+" and unidade = ?1", unidade);
 }

 @POST
 @Transactional
 public Response abrir(CautelaDto d){
  var u = user();
  if(d==null) return erro("Dados da cautela obrigatorios");
  String item = limpar(d.item), responsavel = limpar(d.responsavelNome), categoria = limpar(d.categoria).toUpperCase(Locale.ROOT);
  if(item.isBlank()) return erro("Descricao do item obrigatoria");
  if(responsavel.isBlank()) return erro("Responsavel pela cautela obrigatorio");
  if(categoria.isBlank()) categoria = "OUTROS";
  if(!CATEGORIAS.contains(categoria)) return erro("Categoria invalida. Aceitas: "+String.join(", ", CATEGORIAS));

  // Quem recebe ou e servidor ativo da unidade (matricula conferida no cadastro),
  // ou e declarado externo e ai precisa de documento/empresa. Sem um dos dois o
  // item sai sem ninguem a quem cobrar.
  //
  // A conferencia repete a do formulario de proposito: a tela escolhe da lista, mas
  // uma requisicao montada a mao chegaria aqui com qualquer texto.
  boolean externo = Boolean.TRUE.equals(d.responsavelExterno);
  String identificacao = limpar(d.responsavelMatricula);
  if(externo){
   if(identificacao.isBlank()) return erro("Informe o documento ou a empresa do responsavel externo");
  } else {
   if(identificacao.isBlank()) return erro("Escolha o responsavel na lista de servidores da unidade");
   if(!servidorAtivoDaUnidade(identificacao, u))
    return erro("Matricula "+identificacao+" nao encontrada entre os servidores ativos desta unidade");
  }

  int quantidade = (d.quantidade==null||d.quantidade<1) ? 1 : d.quantidade;
  LocalDateTime saida = parseDataHora(d.dataSaida);
  if(saida==null) saida = LocalDateTime.now();
  LocalDateTime prevista = parseDataHora(d.dataPrevista);
  if(prevista!=null && prevista.isBefore(saida)) return erro("Prazo de devolucao anterior a data de saida");

  var x = new Cautela();
  x.uuid = UUID.randomUUID().toString();
  x.categoria = categoria;
  x.item = item;
  x.identificador = limpar(d.identificador);
  x.quantidade = quantidade;
  x.responsavelNome = responsavel;
  x.responsavelMatricula = identificacao;
  x.responsavelExterno = externo;
  x.setor = limpar(d.setor);
  x.unidade = !limpar(d.unidade).isBlank() ? normalizarUnidade(d.unidade) : normalizarUnidade(u.unidade());
  x.finalidade = limpar(d.finalidade);
  x.observacao = limpar(d.observacao);
  x.situacao = "EM_CAUTELA";
  x.dataSaida = saida;
  x.dataPrevista = prevista;
  x.criadoPor = u.nome();
  x.criadoPorMatricula = u.matricula();
  x.persist();

  LOG.infof("Cautela %s aberta: %s (%s) para %s por %s (%s) na unidade %s",
    x.id, x.item, x.identificador, x.responsavelNome, u.nome(), u.matricula(), x.unidade);
  return Response.status(Response.Status.CREATED).entity(x).build();
 }

 @POST
 @Path("{id}/devolver")
 @Transactional
 public Response devolver(@PathParam("id") String id, CautelaDevolucaoDto dto){
  var u = user();
  Cautela x = buscar(id);
  if(x==null || !daUnidadeDoUsuario(x,u)) throw new NotFoundException();
  if(!"EM_CAUTELA".equals(x.situacao)) return conflito("Cautela ja esta "+rotulo(x.situacao));

  String estado = limpar(dto==null?null:dto.estado).toUpperCase(Locale.ROOT);
  if(estado.isBlank()) estado = "OK";
  if(!ESTADOS_DEVOLUCAO.contains(estado)) return erro("Estado invalido. Aceitos: "+String.join(", ", ESTADOS_DEVOLUCAO));
  // Item que voltou com problema tem que dizer o que houve - e essa observacao
  // que o patrimonio usa para abrir a ocorrencia.
  String observacao = limpar(dto==null?null:dto.observacao);
  if(!"OK".equals(estado) && observacao.isBlank()) return erro("Descreva o ocorrido para devolucao "+rotulo(estado));

  LocalDateTime devolucao = parseDataHora(dto==null?null:dto.dataDevolucao);
  if(devolucao==null) devolucao = LocalDateTime.now();
  if(devolucao.isBefore(x.dataSaida)) return erro("Data de devolucao anterior a data de saida");

  x.situacao = "DEVOLVIDO";
  x.estadoDevolucao = estado;
  x.observacaoDevolucao = observacao;
  x.dataDevolucao = devolucao;
  x.devolvidoPor = u.nome();
  x.devolvidoPorMatricula = u.matricula();

  LOG.infof("Cautela %s devolvida (%s) por %s (%s). Item: %s, responsavel: %s",
    x.id, estado, u.nome(), u.matricula(), x.item, x.responsavelNome);
  return Response.ok(x).build();
 }

 /**
  * Cancelamento e para lancamento errado, nao para item perdido: item que sumiu
  * se devolve como EXTRAVIADO, para continuar aparecendo no historico do responsavel.
  */
 @POST
 @Path("{id}/cancelar")
 @Transactional
 public Response cancelar(@PathParam("id") String id, CautelaCancelamentoDto dto){
  var u = user();
  Cautela x = buscar(id);
  if(x==null || !daUnidadeDoUsuario(x,u)) throw new NotFoundException();
  if(!"EM_CAUTELA".equals(x.situacao)) return conflito("Cautela ja esta "+rotulo(x.situacao));

  String motivo = limpar(dto==null?null:dto.motivo);
  if(motivo.isBlank()) return erro("Motivo do cancelamento obrigatorio");

  x.situacao = "CANCELADO";
  x.motivoCancelamento = motivo;
  x.canceladoPor = u.nome();
  x.canceladoPorMatricula = u.matricula();

  LOG.infof("Cautela %s cancelada por %s (%s). Item: %s. Motivo: %s",
    x.id, u.nome(), u.matricula(), x.item, motivo);
  return Response.ok(x).build();
 }

 /**
  * Servidores ativos da unidade, para o campo de responsavel do formulario.
  *
  * Endpoint proprio em vez de reaproveitar "servidor/nome/{x}/unidade/{u}": aquela
  * rota exige kadm/plistausuarios e devolve o cadastro inteiro (inclusive hash de
  * senha e CPF). Aqui basta pcautela e so saem nome, matricula e cargo - o minimo
  * para preencher o termo.
  */
 @GET
 @Path("servidores")
 public List<Map<String,Object>> servidores(@QueryParam("termo") String termo){
  var u = user();
  String completa = limpar(u.unidade());
  String sigla = normalizarUnidade(completa);

  StringBuilder jpql = new StringBuilder(
    "select s.nome, s.matricula, s.cargo from servidor s where s.ativo = true and s.nome is not null");
  Map<String,Object> params = new HashMap<>();
  restringirUnidadeDoServidor(jpql, params, completa, sigla);
  String busca = limpar(termo);
  if(!busca.isBlank()){
   jpql.append(" and upper(s.nome) like upper(:termo)"); params.put("termo", "%"+busca+"%");
  }
  jpql.append(" order by s.nome");
  var query = entityManager.createQuery(jpql.toString(), Object[].class).setMaxResults(30);
  params.forEach(query::setParameter);

  List<Map<String,Object>> resposta = new ArrayList<>();
  for(Object[] linha : query.getResultList()){
   Map<String,Object> registro = new LinkedHashMap<>();
   registro.put("nome", linha[0]);
   registro.put("matricula", linha[1]);
   registro.put("cargo", linha[2]);
   resposta.add(registro);
  }
  return resposta;
 }

 /**
  * Cautela pertence a unidade onde foi aberta. Quem esta logado em uma unidade
  * nao devolve nem cancela cautela de outra; "TODAS" (perfil global) ve tudo.
  */
 private boolean daUnidadeDoUsuario(Cautela x, AuthenticatedUser u){
  String doUsuario = normalizarUnidade(u.unidade());
  if(doUsuario.isBlank() || "TODAS".equalsIgnoreCase(doUsuario)) return true;
  String daCautela = normalizarUnidade(x.unidade);
  return daCautela.isBlank() || daCautela.equalsIgnoreCase(doUsuario);
 }

 /**
  * A unidade do servidor ora esta gravada so como sigla, ora como "SIGLA:Nome",
  * entao as tres formas contam. Perfil global ("TODAS") ve todo mundo, mesmo
  * criterio da listagem de cautelas.
  */
 private void restringirUnidadeDoServidor(StringBuilder jpql, Map<String,Object> params, String completa, String sigla){
  if(sigla.isBlank() || "TODAS".equalsIgnoreCase(sigla)) return;
  jpql.append(" and (s.unidade = :completa or s.unidade = :sigla or s.unidade like :prefixo)");
  params.put("completa", completa);
  params.put("sigla", sigla);
  params.put("prefixo", sigla+":%");
 }

 /** A matricula informada pertence a um servidor ativo da unidade de quem esta logado? */
 private boolean servidorAtivoDaUnidade(String matricula, AuthenticatedUser u){
  String completa = limpar(u.unidade());
  StringBuilder jpql = new StringBuilder("select count(s) from servidor s where s.ativo = true and s.matricula = :matricula");
  Map<String,Object> params = new HashMap<>();
  params.put("matricula", matricula);
  restringirUnidadeDoServidor(jpql, params, completa, normalizarUnidade(completa));
  var query = entityManager.createQuery(jpql.toString(), Long.class);
  params.forEach(query::setParameter);
  Long total = query.getSingleResult();
  return total != null && total > 0;
 }

 private Cautela buscar(String id){
  if(id==null || id.isBlank()) return null;
  try{ return Cautela.findById(Integer.valueOf(id)); }
  catch(NumberFormatException ignored){ return Cautela.find("uuid", id).firstResult(); }
 }

 private String normalizarUnidade(String unidade){
  String valor = limpar(unidade);
  return valor.isBlank() ? "" : valor.split(":")[0].trim();
 }

 private String rotulo(String situacao){
  return "DEVOLVIDO".equals(situacao) ? "devolvida"
    : "CANCELADO".equals(situacao) ? "cancelada"
    : "AVARIADO".equals(situacao) ? "avariada"
    : "EXTRAVIADO".equals(situacao) ? "extraviada"
    : String.valueOf(situacao).toLowerCase(Locale.ROOT);
 }

 private Response erro(String mensagem){ return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", mensagem)).build(); }
 private Response conflito(String mensagem){ return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", mensagem)).build(); }
 private String limpar(String v){ return String.valueOf(v==null?"":v).trim(); }
 private LocalDate parseData(String data){ try{ return (data==null||data.isBlank())?null:LocalDate.parse(data.substring(0,10)); }catch(Exception e){ return null; } }

 /** Aceita "yyyy-MM-ddTHH:mm" (input datetime-local) e "yyyy-MM-dd" (input date). */
 private LocalDateTime parseDataHora(String valor){
  String v = limpar(valor);
  if(v.isBlank()) return null;
  try{ return LocalDateTime.parse(v.length()>16 ? v.substring(0,16) : v); }
  catch(Exception ignored){}
  LocalDate data = parseData(v);
  return data==null ? null : data.atStartOfDay();
 }
}

