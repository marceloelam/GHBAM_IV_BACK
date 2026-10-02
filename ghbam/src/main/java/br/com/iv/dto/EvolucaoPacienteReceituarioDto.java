package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class EvolucaoPacienteReceituarioDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private LocalDate data;
	@Column(columnDefinition = "TEXT")
	private String relato;
	private int nbam;
	@Column(length = 5) // nullable=false - Não pode ser nulo
	private String horaev;
	private String medicocrm;
	private String unidade;

}
