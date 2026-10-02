package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class SigtapProcedimentoImportDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String paId;
    private String paDv;
    private String paDc;
    private double paSa;
    private double paSp;
    private double paSh;
    private String paEletiva;
    private String paTpreg;
    private String tpSexo;
    private Integer idadeMinima;
    private Integer idadeMaxima;
    private Integer quantidadeMaxima;
    private String tpComplexidade;
    private String coFinanciamento;
}
