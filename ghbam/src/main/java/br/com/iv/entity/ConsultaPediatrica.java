package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "ConsultaPediatrica")
public class ConsultaPediatrica extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

	private int idbam;
	private int idpaciente;
	@Column(length = 40)
	private String prontuario;
	private int nbam;
	@Column(length = 20)
	private String unidade;

	@Temporal(TemporalType.TIMESTAMP)
	private Date dataConsulta;
	private Integer idadeMeses;

	private Double peso;
	private Double estatura;
	private Double perimetroCefalico;
	private Double imc;

	private Integer idadeGestacionalSemanas;
	@Column(length = 20)
	private String tipoParto;
	private Double pesoNascimento;
	private Double estaturaNascimento;
	private Double perimetroCefalicoNascimento;
	private Integer apgar1;
	private Integer apgar5;

	// Antecedentes gestacionais
	private Integer numeroGestacoes;
	private Integer numeroPartos;
	private Integer numeroAbortos;
	private Boolean fumouGestacao;
	@Column(length = 20)
	private String hiv;
	@Column(length = 20)
	private String rubeola;
	@Column(length = 20)
	private String toxoplasmose;
	@Column(length = 20)
	private String cmv;
	@Column(length = 20)
	private String sifilis;
	@Column(length = 4000)
	private String outrosAntecedentesGestacionais;
	private Boolean intercorrenciasGestacao;
	@Column(length = 4000)
	private String medicacoesGestacao;

	// Antecedentes do recem-nascido
	@Column(length = 30)
	private String permanenciaNeonatal;
	private Double pesoAlta;
	private Double deltaPesoAlta;
	private Double perimetroToracico;
	@Column(length = 10)
	private String classificacaoPeso;
	private Boolean intercorrenciasNeonatais;
	@Column(length = 4000)
	private String intercorrenciasNeonataisObs;
	@Column(length = 20)
	private String classificacaoIdadeGestacional;

	// Triagem neonatal
	@Column(length = 20)
	private String triagemAuditiva;
	@Column(length = 20)
	private String reflexoVermelho;
	@Column(length = 10)
	private String tipagemSanguineaMae;
	@Column(length = 10)
	private String tipagemSanguineaRn;
	@Column(length = 20)
	private String coombs;
	@Column(length = 20)
	private String eluato;
	@Column(length = 20)
	private String oximetria;
	@Column(length = 30)
	private String quedaCoto;
	@Column(length = 20)
	private String testePezinho;
	@Column(length = 20)
	private String testeLinguinha;
	@Column(length = 4000)
	private String diagnosticoNeonatalOutros;

	// Rotina infantil
	private Boolean ficaEmCasa;
	private Boolean tomaSol;
	private Boolean habitoOral;
	@Column(length = 10)
	private String horaAcorda;
	private Boolean chupeta;
	private Boolean mamadeira;
	private Boolean vaiEscola;
	@Column(length = 15)
	private String periodoEscola;
	@Column(length = 15)
	private String comoSono;
	private Boolean praticaEsporte;
	private Boolean fazEducacaoFisica;
	@Column(length = 4000)
	private String sedentarismo;
	@Column(length = 4000)
	private String diureseEvacuacao;
	@Column(length = 4000)
	private String higieneBucal;

	// Alimentacao detalhada
	@Column(length = 15)
	private String tipoAleitamento;
	private Boolean comeCarnes;
	private Boolean comeOvos;
	private Boolean comeVerdurasLegumes;
	private Boolean comeLeites;
	private Boolean comeFrutas;
	private Boolean comeDocesGuloseimas;
	@Column(length = 4000)
	private String alimentacaoOutros;

	// Exame fisico completo
	private Double temperatura;
	private Integer pressaoSentadoSistolica;
	private Integer pressaoSentadoDiastolica;
	private Integer pressaoDeitadoSistolica;
	private Integer pressaoDeitadoDiastolica;
	private Double circunferenciaAbdominal;
	private Double alturaPai;
	private Double alturaMae;
	@Column(length = 4000)
	private String aspectoGeral;
	@Column(length = 4000)
	private String otoscopia;
	@Column(length = 4000)
	private String cavidadeOral;
	@Column(length = 4000)
	private String mamas;
	@Column(length = 4000)
	private String cardiovascular;
	@Column(length = 4000)
	private String respiratorio;
	@Column(length = 4000)
	private String abdome;
	@Column(length = 4000)
	private String genital;
	@Column(length = 4000)
	private String extremidades;
	@Column(length = 4000)
	private String exameOutrasObservacoes;

	// Diagnosticos estruturados
	@Column(length = 15)
	private String diagnosticoNutricional;
	private Boolean nutricaoAdequada;
	private Boolean crescimentoAdequado;
	private Boolean vacinacaoAtualizada;

	@Column(length = 4000)
	private String queixaPrincipal;
	@Column(length = 4000)
	private String hda;
	@Column(length = 4000)
	private String alimentacao;
	@Column(length = 4000)
	private String historicoVacinal;
	@Column(length = 4000)
	private String historicoFamiliar;

	@Column(length = 20)
	private String dnpmMotorGrosso;
	@Column(length = 20)
	private String dnpmMotorFino;
	@Column(length = 20)
	private String dnpmLinguagem;
	@Column(length = 20)
	private String dnpmPessoalSocial;
	@Column(length = 4000)
	private String dnpmObservacao;

	@Column(length = 4000)
	private String hipoteseDiagnostica;
	@Column(length = 10)
	private String cid;
	@Column(length = 4000)
	private String conduta;

	@Column(length = 120)
	private String medico;
	@Column(length = 15)
	private String crm;
	@Column(length = 40)
	private String criadoPorMatricula;

	@Temporal(TemporalType.TIMESTAMP)
	private Date criadoEm;
	@Temporal(TemporalType.TIMESTAMP)
	private Date atualizadoEm;

	public ConsultaPediatrica() {
		super();
	}

	@PrePersist
	protected void aoPersistir() {
		if (criadoEm == null) {
			criadoEm = new Date();
		}
		atualizadoEm = new Date();
		calcularImc();
	}

	@PreUpdate
	protected void aoAtualizar() {
		atualizadoEm = new Date();
		calcularImc();
	}

	private void calcularImc() {
		if (peso == null || estatura == null || estatura == 0) {
			return;
		}
		double estaturaMetros = estatura / 100.0;
		imc = peso / (estaturaMetros * estaturaMetros);
	}

}
