package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.Data;
import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.Prontuario;
import br.com.iv.entity.Registro;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Tratamento;

@Data
public class BamDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Temporal(TemporalType.DATE)
	private Date data;
	@Column(length = 5)
	private String hora;
	@Column(length = 40)
	private String controle;
	private boolean internar;
	private boolean darAlta;
	@Column(length = 12)
	private String dataDaAlta;
	private int printTriagem;
	@Column(length = 55)
	private String nAtendente;
	private int cor;
	@Column(length = 6000)
	private String receita;
	private boolean ematendimento;
	@Column(length = 35)
	private String unidade;
	private int prioridade;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Registro registro;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Avaliacao avaliacao;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Pacientes paciente;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Tratamento tratamento;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Servidor servidor;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Prontuario prontuario;
	private boolean transferido;
	@Column(length = 800)
	private String comentariotranferencia;

}
