package br.com.iv.dto;

import lombok.Data;

@Data
public class PushDispositivoDto {
    private Long id;
    private String matricula;
    private String endpoint;
    private Boolean ativo;
}
