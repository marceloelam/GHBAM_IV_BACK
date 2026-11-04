package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class PrescricaoItemEstruturadoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int iditem;
    private Integer idprescricao;
    private Integer idpaciente;
    private int idbam;
    private String unidade;
    private String status;
    private String tipoItem;
    private String medicamento;
    private String dose;
    private String via;
    private String frequencia;
    private String duracao;
    private String observacao;
    private String setorExecucao;
    private String prescritoPor;
    private String prescritoPorMatricula;
    private String atualizadoPor;
    private String atualizadoPorMatricula;
    private String suspensoPor;
    private String suspensoPorMatricula;
    private String motivoSuspensao;
}
