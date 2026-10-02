package br.com.iv.dto;

import lombok.Data;

@Data
public class GedRemocaoDto {
 public String motivo;
 // Reautenticacao: remover documento do GED exige a senha de quem esta logado.
 public String senha;
}
