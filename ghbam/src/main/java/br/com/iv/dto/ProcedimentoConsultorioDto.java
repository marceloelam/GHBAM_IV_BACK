package br.com.iv.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProcedimentoConsultorioDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String unidade;
    private String codigo;
    private String nome;
    private String especialidade;
    private String tipoCobranca;
    private BigDecimal valorPadrao;
    private BigDecimal valorRepassePadrao;
    private String ativo;
    private String usuario;
}
