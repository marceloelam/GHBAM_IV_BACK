package br.com.iv.dto;

import lombok.Data;

@Data
public class ConsultaPediatricaDto {

	private int idbam;
	private int idpaciente;
	private String prontuario;
	private int nbam;
	private String unidade;

	private Integer idadeMeses;

	private Double peso;
	private Double estatura;
	private Double perimetroCefalico;

	private Integer idadeGestacionalSemanas;
	private String tipoParto;
	private Double pesoNascimento;
	private Double estaturaNascimento;
	private Double perimetroCefalicoNascimento;
	private Integer apgar1;
	private Integer apgar5;

	private Integer numeroGestacoes;
	private Integer numeroPartos;
	private Integer numeroAbortos;
	private Boolean fumouGestacao;
	private String hiv;
	private String rubeola;
	private String toxoplasmose;
	private String cmv;
	private String sifilis;
	private String outrosAntecedentesGestacionais;
	private Boolean intercorrenciasGestacao;
	private String medicacoesGestacao;

	private String permanenciaNeonatal;
	private Double pesoAlta;
	private Double deltaPesoAlta;
	private Double perimetroToracico;
	private String classificacaoPeso;
	private Boolean intercorrenciasNeonatais;
	private String intercorrenciasNeonataisObs;
	private String classificacaoIdadeGestacional;

	private String triagemAuditiva;
	private String reflexoVermelho;
	private String tipagemSanguineaMae;
	private String tipagemSanguineaRn;
	private String coombs;
	private String eluato;
	private String oximetria;
	private String quedaCoto;
	private String testePezinho;
	private String testeLinguinha;
	private String diagnosticoNeonatalOutros;

	private Boolean ficaEmCasa;
	private Boolean tomaSol;
	private Boolean habitoOral;
	private String horaAcorda;
	private Boolean chupeta;
	private Boolean mamadeira;
	private Boolean vaiEscola;
	private String periodoEscola;
	private String comoSono;
	private Boolean praticaEsporte;
	private Boolean fazEducacaoFisica;
	private String sedentarismo;
	private String diureseEvacuacao;
	private String higieneBucal;

	private String tipoAleitamento;
	private Boolean comeCarnes;
	private Boolean comeOvos;
	private Boolean comeVerdurasLegumes;
	private Boolean comeLeites;
	private Boolean comeFrutas;
	private Boolean comeDocesGuloseimas;
	private String alimentacaoOutros;

	private Double temperatura;
	private Integer pressaoSentadoSistolica;
	private Integer pressaoSentadoDiastolica;
	private Integer pressaoDeitadoSistolica;
	private Integer pressaoDeitadoDiastolica;
	private Double circunferenciaAbdominal;
	private Double alturaPai;
	private Double alturaMae;
	private String aspectoGeral;
	private String otoscopia;
	private String cavidadeOral;
	private String mamas;
	private String cardiovascular;
	private String respiratorio;
	private String abdome;
	private String genital;
	private String extremidades;
	private String exameOutrasObservacoes;

	private String diagnosticoNutricional;
	private Boolean nutricaoAdequada;
	private Boolean crescimentoAdequado;
	private Boolean vacinacaoAtualizada;

	private String queixaPrincipal;
	private String hda;
	private String alimentacao;
	private String historicoVacinal;
	private String historicoFamiliar;

	private String dnpmMotorGrosso;
	private String dnpmMotorFino;
	private String dnpmLinguagem;
	private String dnpmPessoalSocial;
	private String dnpmObservacao;

	private String hipoteseDiagnostica;
	private String cid;
	private String conduta;

	private String medico;
	private String crm;

}
