package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ProcedimentoSusValorEfetivoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idprocedimentossus;
    private Integer idprocedimentovalorunidade;
    private String unidade;
    private String pa_id;
    private String pa_dv;
    private String pa_dc;
    private double pa_sa_padrao;
    private double pa_sp_padrao;
    private double pa_sh_padrao;
    private double pa_sa;
    private double pa_sp;
    private double pa_sh;
    private String pa_eletiva;
    private String pa_tpreg;
    private boolean personalizado;
    private boolean usarValorPersonalizado;
    private boolean usadoRx;
    private String descricaoRx;
    private String modoRx;
    private String modo2Rx;
}
