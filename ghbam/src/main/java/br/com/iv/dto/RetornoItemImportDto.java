package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class RetornoItemImportDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String codigoExterno;
    private Integer idafaturar;
    private double valorApresentado;
    private double valorAprovado;
    private String resultado;
    private String motivo;
    private String codigoCritica;
}
