package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ProcedimentoSusValorUnidadeDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String unidade;
    private String pa_id;
    private String pa_dv;
    private double pa_sa;
    private double pa_sp;
    private double pa_sh;
    private boolean usarValorPersonalizado;
    private String atualizadoPor;
}
