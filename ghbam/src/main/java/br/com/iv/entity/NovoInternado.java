package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "NovoInternado")
public class NovoInternado extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idninternado;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private SetorCenso setor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "local_idsetorlocalcenso", nullable = false)
	private LocalCenso local;

	@Column(length = 18)
	private String internado;

	@Column(length = 5)
	private String especialidade;

	@Column(length = 65)
	private String diagnostico;

	private LocalDate diainternacao;

	@Column(length = 20)
	private String prontuario;

	private int tempoDias;

	private LocalDate datainsercaoser;

	private LocalDate datadaacao;

	@Column(length = 20)
	private String leitoocupado;

	@Column(length = 10)
	private String agente;

	@Column(length = 15)
	private String tipo;

	@Column(length = 20)
	private String motivosaida;

	@Column(length = 500)
	private String pendencia;

	@Column(length = 25)
	private String unidade;

	private boolean alta;

	private int nbam;
	@Column(length = 185)
	private String quemalocou;
	@Column(length = 185)
	private String quemdeualta;
	@Column(length = 185)
	private String quemleitoalterou;
	@Column(length = 185)
	private String quemexcluiu;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Pacientes pacientes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Bam bam;

	@Column(length = 25)
	private String tipo_alocacao;

	public NovoInternado() {
		super();
		bam = new Bam();
		pacientes = new Pacientes();
		local = new LocalCenso();
		setor = new SetorCenso();
		prontuario = "";
		diagnostico = "";
		tempoDias = 0;
		leitoocupado = "";
		tipo_alocacao = "";
		pendencia = "";
		unidade = "";
		motivosaida = "-";
		especialidade = "N.INF";
		alta = false;
		agente = "PADRÃO";
		tipo = "N.INF";
		quemalocou = "";
		quemdeualta = "";
		quemleitoalterou = "";
		quemexcluiu = "";
		LocalDate hoje = LocalDate.now();
		diainternacao = hoje;
		datadaacao = hoje;
		datainsercaoser = hoje;
	}

	public String montarInternado() {
		// Implementar lógica de montagem de internado
		return null;
	}
}
