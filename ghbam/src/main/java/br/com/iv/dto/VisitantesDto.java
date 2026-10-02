package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class VisitantesDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idvisitante;
	@Column(length = 55)
	private String nomeVisitante;
	@Column(length = 18)
	private String cpfVisitante;
	@Column(length = 18)
	private String identidadeVisitante;
	@Column(length = 12)
	private String dataVisita;
	@Column(length = 5)
	private String horaVisitante;
	@Column(length = 12)
	private String nascimentoVisitante;
	@Column(length = 20)
	private String nbam;
	@Column(length = 55)
	private String obsvisitante;
	@Column(length = 100)
	private String endereco;
	@Column(length = 30)
	private String telefone;
	@Column(length = 800)
	private String fotovisitante;
	@Column(length = 100)
	private String fotovisitantearquivo;
	@Column(length = 30)
	private String nunidade;

}
