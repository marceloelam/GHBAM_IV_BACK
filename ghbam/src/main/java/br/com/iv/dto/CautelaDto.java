package br.com.iv.dto;

import lombok.Data;

@Data
public class CautelaDto {
 public String categoria;
 public String item;
 public String identificador;
 public Integer quantidade;
 public String responsavelNome;
 public String responsavelMatricula;
 /** true quando o responsavel nao e servidor; ai responsavelMatricula e documento/empresa. */
 public Boolean responsavelExterno;
 public String setor;
 public String unidade;
 public String finalidade;
 public String observacao;
 /** ISO local (yyyy-MM-ddTHH:mm) ou yyyy-MM-dd; vazio = agora. */
 public String dataSaida;
 /** Prazo de devolucao. Vazio = sem prazo, entra em pendencia mas nunca vence. */
 public String dataPrevista;
}
