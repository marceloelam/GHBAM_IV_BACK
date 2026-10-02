package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import br.com.iv.entity.Pacientes;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class SalvarEscalaPaciente2Dto implements Serializable {
    private static final long serialVersionUID = 1L;
    private String titulo;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate inicioagenda;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fimagenda;
    @Column(length = 15)
    private String dt_nasc;
    @Column(length = 5)
    private String horamarcada;
    @Column(length = 5)
    private String horasaida;
    @Column(length = 30)
    private String setor;
    private Pacientes paciente;
    private String medico;
    private String cadastrou;
    @Column(length = 30)
    private String unidade;
    @Column(length = 600)
    private String obs;
    @Column(length = 600)
    private String obs2;
    private boolean statusevento;
    private boolean chegou;
    private int agbam;
    private boolean atendido;
    private boolean agenda;
    private int idDoMedico;
    @Column(length = 1000)
    private String comentario;

    @Column(length = 20)
    private String modalidade;

}
