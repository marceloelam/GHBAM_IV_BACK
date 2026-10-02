package br.com.iv.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class FhirEnvioFilaStatusDto {
    private Long idfhirenviofila;
    private String tipoDocumento;
    private Integer iddocumento;
    private Integer idbam;
    private Integer idpaciente;
    private String nomePaciente;
    private String unidade;
    private String status;
    private Integer statusHttp;
    private Integer tentativas;
    private LocalDateTime proximaTentativaEm;
    private LocalDateTime criadoEm;
    private LocalDateTime enviadoEm;
    // Resposta do destino ou motivo da rejeicao local (validador/autenticacao), truncada.
    // Nunca o payload clinico enviado.
    private String motivo;
}
