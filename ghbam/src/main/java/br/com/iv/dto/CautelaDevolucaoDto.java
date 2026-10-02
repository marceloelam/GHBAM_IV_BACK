package br.com.iv.dto;

import lombok.Data;

@Data
public class CautelaDevolucaoDto {
 /** OK | AVARIADO | EXTRAVIADO */
 public String estado;
 public String observacao;
 /** Vazio = agora. Permite lancar devolucao que aconteceu no balcao ontem. */
 public String dataDevolucao;
}
