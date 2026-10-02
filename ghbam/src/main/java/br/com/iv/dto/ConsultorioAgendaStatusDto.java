package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import lombok.Data;

@Data
public class ConsultorioAgendaStatusDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String unidade;
    private Integer idescalaPaciente;
    private LocalDate dataAtendimento;
    private String paciente;
    private String profissional;
    private String especialidade;
    private String status;
    private String observacao;
    private String usuario;
}
