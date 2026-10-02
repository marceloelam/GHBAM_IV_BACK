package br.com.iv.dto;

import lombok.Data;

@Data
public class FhirEnvioDestinoDto {
    private Integer idfhirenviodestino;
    private String unidade;
    private String finalidade;
    private String ambiente;
    private String endpointBase;
    private String tipoAutenticacao;
    private String credencial;
    private String apiKeyHeaderNome;
    private Boolean ativo;
    private Boolean possuiCredencial;
}
