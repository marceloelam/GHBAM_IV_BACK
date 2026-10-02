package br.com.iv.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class TissContaItemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idtissconta;
    private String tipoItem;
    private String tabela;
    private String codigo;
    private String descricao;
    private BigDecimal quantidade;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private String dataExecucao;
    private String executante;
    private String conselho;
    private String ufConselho;
    private String cbo;
    private String numeroautorizacao;
    private String origem;
    private String origemId;
    private String status;
    private String observacao;
}
