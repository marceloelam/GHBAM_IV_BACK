package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "bam")
public class Bam extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idbam;
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
	@Column(length = 35)
	private String transorigem;

	// Ao inv�s de criar chave estrangeira criar persistencia, ou seja, Composi��o
	// Nome da classe com o objeto
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false)
	private Registro registro;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false)
	private Avaliacao avaliacao;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false)
	private Pacientes paciente;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false)
	private Tratamento tratamento;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false)
	private Servidor servidor;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = true)
	private Prontuario prontuario;
	private boolean transferido;
	@Column(length = 800)
	private String comentariotranferencia;

	public Bam() {
		super();
		iniciarAtributos();
	}

	private void iniciarAtributos() {
		registro = new Registro();
		avaliacao = new Avaliacao();
		paciente = new Pacientes();
		tratamento = new Tratamento();
		servidor = new Servidor();
		prontuario = null;
		transferido = false;
		comentariotranferencia = "";
		controle = "";
		idbam = 0;
		darAlta = false;
		internar = false;
		printTriagem = 0;
		nAtendente = "";
		cor = 1;
		receita = "";
		ematendimento = false;
		unidade = "";
		transorigem = "";

	}

}
