package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class SigtapImportarDatasusRequestDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String competencia;
    private String unidade;
    private String arquivo;
    private String url;
    private String observacao;
}
