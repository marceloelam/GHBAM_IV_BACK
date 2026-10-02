package br.com.iv.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class TissContaDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int bam;
    private int idpaciente;
    private String nomepaciente;
    private String unidade;
    private String competencia;
    private String tipo;
    private String status;
    private String operadora;
    private String cnesprestador;
    private String registroans;
    private String plano;
    private String numerocarteira;
    private String numeroautorizacao;
    private String dataabertura;
    private String datafechamento;
    private String criadoPor;
    private String atualizadoPor;
    private String origem;
    private String observacao;
    private BigDecimal valorTotal;
}
