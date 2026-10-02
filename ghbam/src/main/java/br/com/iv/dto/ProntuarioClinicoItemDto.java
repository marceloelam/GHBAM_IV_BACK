package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ProntuarioClinicoItemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int iditem;
    private int idpaciente;
    private Integer idbam;
    private String unidade;
    private String tipo;
    private String status;
    private String codigo;
    private String titulo;
    private String descricao;
    private String observacao;
    private String criadoPor;
    private String criadoPorMatricula;
    private String atualizadoPor;
    private String atualizadoPorMatricula;
}
