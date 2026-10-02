package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class ResumoFinalAvaliacaoMedicoDto implements Serializable {
	private static final long serialVersionUID = 1L;
	@Column(length = 13)
	private String dataAtendimento;
	@Column(length = 5)
	private String horasaida;
	private int totalatendimentos;
	@Column(length = 55)
	private String medico;
	@Column(length = 25)
	private String setor;
	@Column(length = 25)
	private String unidade;
}
