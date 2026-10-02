package br.com.iv.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Projecao da listagem do GED sem o conteudo do arquivo. A entidade guarda o
 * anexo em conteudo_base64 (TEXT) e devolver esse campo na lista fazia o GET
 * trafegar todos os arquivos da pagina. O conteudo agora sai apenas pelo
 * endpoint ged/documentos/{id}/conteudo.
 */
@Data
public class GedDocumentoResumoDto {
 public Integer id;
 public String uuid;
 public String tipo;
 public String status;
 public Integer idpaciente;
 public Integer idbam;
 public String prontuario;
 public String nomePaciente;
 public String unidade;
 public String nomeArquivo;
 public String tipoMime;
 public Long tamanhoBytes;
 public String descricao;
 public String criadoPor;
 public String criadoPorMatricula;
 public LocalDateTime criadoEm;
 public LocalDateTime atualizadoEm;

 public GedDocumentoResumoDto(Integer id, String uuid, String tipo, String status, Integer idpaciente, Integer idbam,
   String prontuario, String nomePaciente, String unidade, String nomeArquivo, String tipoMime, Long tamanhoBytes,
   String descricao, String criadoPor, String criadoPorMatricula, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
  this.id = id;
  this.uuid = uuid;
  this.tipo = tipo;
  this.status = status;
  this.idpaciente = idpaciente;
  this.idbam = idbam;
  this.prontuario = prontuario;
  this.nomePaciente = nomePaciente;
  this.unidade = unidade;
  this.nomeArquivo = nomeArquivo;
  this.tipoMime = tipoMime;
  this.tamanhoBytes = tamanhoBytes;
  this.descricao = descricao;
  this.criadoPor = criadoPor;
  this.criadoPorMatricula = criadoPorMatricula;
  this.criadoEm = criadoEm;
  this.atualizadoEm = atualizadoEm;
 }
}
