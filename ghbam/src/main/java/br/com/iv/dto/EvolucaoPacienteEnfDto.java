package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class EvolucaoPacienteEnfDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 30)
	private String leito;
	private LocalDate data;
	@Column(length = 35)
	private String prontuario;
	@Column(columnDefinition = "TEXT")
	private String relato;
	@Column(length = 30)
	private String enfermaria;
	@Column(length = 60)
	private String enfermeiro;
	private int nbam;
	@Column(length = 5)
	private String hora;
	@Column(length = 15)
	private String quem;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedido;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidolimpo;
	@Column(length = 40) // nullable=false - Não pode ser nulo
	private String local;
	@Column(length = 65) // nullable=false - Não pode ser nulo
	private String medico;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String crm;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidodomed;
	@Column(length = 35) // nullable=false - Não pode ser nulo
	private String unidade;

	@Column(columnDefinition = "TEXT")
	private String motivoRetificacao;

}
