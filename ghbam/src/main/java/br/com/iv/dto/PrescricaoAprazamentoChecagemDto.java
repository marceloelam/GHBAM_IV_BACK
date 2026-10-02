package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PrescricaoAprazamentoChecagemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idaprazamentochecagem;
    private int idprescricao;
    private Integer idbam;
    private Integer idpaciente;
    private int indiceHorario;
    private String horarioAprazado;
    private String observacaoAprazamento;
    private String aprazadoPor;
    private String aprazadoPorMatricula;
    private LocalDateTime aprazadoEm;
    private boolean checado;
    private LocalDateTime checadoEm;
    private String checadoPor;
    private String checadoPorMatricula;
    private String observacaoChecagem;
}
