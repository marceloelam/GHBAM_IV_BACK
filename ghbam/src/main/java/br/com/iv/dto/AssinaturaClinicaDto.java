package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class AssinaturaClinicaDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idassinatura;
    private String tipoDocumento;
    private Integer iddocumento;
    private Integer idbam;
    private Integer idpaciente;
    private String unidade;
    private String conteudoResumo;
    private String conteudoHash;
    private String assinadoPor;
    private String assinadoPorMatricula;
    private String adendoTexto;
    private String adendoPor;
    private String adendoPorMatricula;
    private String assinaturaDigital;
    private String algoritmoAssinatura;
    private String chavePublica;
    private Integer assinaturaOrigemId;
}
