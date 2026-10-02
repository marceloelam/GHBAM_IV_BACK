package br.com.iv.dto;

import lombok.Data;

@Data
public class ResetSenhaSolicitacaoDto {
    private Long id;
    private String matricula;
    private String status;
    private String codigoConfirmacao;
}
