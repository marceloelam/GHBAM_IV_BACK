package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Data;

@Data
public class ConfiguracaoAgendaConsultorioDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idconfiguracaoagendaconsultorio;
    private String unidade;
    private String profissional;
    private String especialidade;
    private String sala;
    private String diaSemana;
    private LocalDate dataAgenda;
    private LocalTime horaInicio;
    private LocalTime horaFim;
    private Integer duracaoMinutos;
    private Integer limiteEncaixes;
    private Integer intervaloRetornoDias;
    private String permiteEncaixe;
    private String ativo;
    private String observacao;
    private String usuario;
}
