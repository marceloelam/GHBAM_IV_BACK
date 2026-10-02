package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class EvolucaoPacienteDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private String leito;
	private LocalDate data;
	@Column(length = 35)
	private String prontuario;
	@Column(length = 13000)
	private String relato;
	@Column(length = 30)
	private String enfermaria;
	private int nbam;
	private boolean alta;
	private boolean inativo;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedido;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidolimpo;
	@Column(length = 20) // nullable=false - Não pode ser nulo
	private String local;
	@Column(length = 65) // nullable=false - Não pode ser nulo
	private String medico;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String crm;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidodomed;
	@Column(length = 5) // nullable=false - Não pode ser nulo
	private String horaev;
	@Column(length = 35) // nullable=false - Não pode ser nulo
	private int qtde;
	private String unidade;

}
