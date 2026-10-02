package br.com.iv.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProcedimentoConsultorioPrecoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String unidade;
    private Integer idprocedimentoConsultorio;
    private String tipoCobranca;
    private String convenio;
    private String plano;
    private BigDecimal valor;
    private BigDecimal valorRepasse;
    private String ativo;
    private String usuario;
}
