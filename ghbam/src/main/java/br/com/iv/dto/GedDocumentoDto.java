package br.com.iv.dto;

import lombok.Data;

@Data
public class GedDocumentoDto {
 public Integer id;
 public String uuid;
 public String tipo;
 public String status;
 public Integer idpaciente;
 public Integer idbam;
 public String prontuario;
 public String nomePaciente;
 public String nomepaciente;
 public String unidade;
 public String nomeArquivo;
 public String tipoMime;
 public Long tamanhoBytes;
 public String descricao;
 public String conteudoBase64;
}
