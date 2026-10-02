package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class AnotacaoEnfermagemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int idpaciente;
    private Integer idbam;
    private String prontuario;
    private String nomepaciente;
    private String unidade;
    private String status;
    private String anotacao;
    private String motivo;
    private String criadoPor;
    private String criadoPorMatricula;
    private String atualizadoPor;
    private String atualizadoPorMatricula;
}
