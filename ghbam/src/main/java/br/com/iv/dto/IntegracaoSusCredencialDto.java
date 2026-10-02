package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class IntegracaoSusCredencialDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idintegracaosuscredencial;
    private String unidade;
    private String ambiente;
    private String orgaoGestor;
    private String usuarioTecnico;
    private String clientId;
    private String clientSecret;
    private String certificadoAlias;
    private String certificadoPfxBase64;
    private String certificadoSenha;
    private String endpointBase;
    private Boolean ativo;
    private Boolean possuiClientSecret;
    private Boolean possuiCertificado;
}
