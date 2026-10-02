package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import br.com.iv.entity.Pacientes;
import jakarta.persistence.Column;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

@Data
public class EscalaPacienteDto implements Serializable {
	private static final long serialVersionUID = 1L;
	private String titulo;
	@Temporal(TemporalType.DATE)
	private Date inicioagenda;
	@Temporal(TemporalType.DATE)
	private Date fimagenda;
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

	public Pacientes getPaciente() {
	    return paciente;
	}

	public void setPaciente(Pacientes paciente) {
	    this.paciente = paciente;
	}
	
}
