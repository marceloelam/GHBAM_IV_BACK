package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.xml.bind.annotation.XmlRootElement;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "servidor2")
@XmlRootElement
public class Servidor2 extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 8697783797202417749L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int idservidor2;
// Nenhum valor padrão deve ser definido para idservidor2 na entidade. Certifique-se que no banco de dados não há DEFAULT na coluna idservidor2.
	@Column(length = 15, nullable = false)
	private String matricula;
	@Column(length = 55)
	private String nome;
	@Column(length = 15)
	private String tel;
	@Column(length = 55)
	private String email;
	@Column(length = 30)
	private String unidade;
	@Column(length = 16)
	private String cns;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean superusuario;


	// Define as permissões ADMINISTRATIVAS
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cadsalas;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cadsetores;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cadunidades;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cadusuarios;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean caddrives;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean auditoriaclinica;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean listausuarios;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean mapapacientes;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean resumopaciente;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean aguardandoatendimento;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean atendimentoperiodo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean biometria;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean comunicacaoespecialistas;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean correcaobam;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean enfermariaseleitos;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean imprimirbam;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean limparpainel;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pacientesematendimento;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean avaliacaopacienteia_adm;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pendenciaassinatura;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean historicocompleto_adm;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean prontuariocompleto;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean exportardadoslgpd;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean comoestaopaciente;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean verificarassinatura;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean ragprontuario;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean resumoclinicoia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean personalizacaovisual;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pacs;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcentrocirurgico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiaagendar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiaexecutar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiadescrever;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiaanestesia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiasuspender;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiaopme;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgiasalas;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcirurgialistaespera;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean papoioassistencial;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pcme;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean phemoterapia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean psnd;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean prnds;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pnsp;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pnspgestao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pnspclassificar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pnspbloqueado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pfhirsumarioalta;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean logs;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean vigilanciaepidemiologica;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean configuracaovigilanciaepidemiologica;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean producaomedica;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean integracaosus;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean migracaodados;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean ultrassonografia;

	// Define as permissões NIR
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cadleitos;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cadsetor;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean restaurarpaciente;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean biexecutivo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean nirrestaurarinternacao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean ged;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gedremover;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gedconsulta;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean cautela;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean nir;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean painelnir;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean localcenso;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pesquisanir;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean nirrelatorio;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean setorcenso;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean internadosvisitaimp;

	// Define as permissões Enfermagem
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean balancohidrico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean ccih;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean driveenfermagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean escalaenfermagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean evolucaomultidisciplinar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean pedidosexame;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean minhasassinaturas;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean avaliacaopacienteia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean bulario;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean bulariomedico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean ciclomedicacao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean historicocompleto;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean anotacoesenfermagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean saeenfermagem;

	private Boolean classificacaoghbam;

	private Boolean classificacaoghbam_revisar;

	private Boolean classificacaoghbam_aprovar;

	private Boolean classificacaoghbam_admin;

	private Boolean classificacaoghbam_auditar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean controleassistencial;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean sinan;
	private Boolean documentosassistenciais;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean painelenfermagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean painelspa;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean painelmaternidade;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean painelpediatria;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean modeloevolucaoenf;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean modeloprescricaoenf;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean resumopacientes;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultorioadmin;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultoriorecepcao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultoriomedico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultoriofinanceiro;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean chatprofissionais;

	// Define as permissões Monitoramento e RH
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean monitoramento;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean controleplantoes;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean produtividaderh;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean treinamentocertificacao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean rh;

	// Define permissões de Certificados Digitais
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gerenciarcertificados;

	// Define as permissões do módulo Faturamento SUS (Central Municipal de
	// Faturamento SUS - Fase 1). Visualização reaproveita a permissão
	// "faturamento" já existente; estas são as ações mais sensíveis.
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamentosuslancar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamentosuscorrigir;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamentosusauditar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamentosusfechar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamentosusreabrir;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean sigtapimportar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean bpaexportar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean apacautorizar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean aihauditar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamentosusimportarretorno;
	// GHBAM APS (Atencao Primaria) - V171
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean aps;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean apsacs;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean apsvacina;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean apsgestao;
	// G-PEC: permissao por parte do modulo - V174
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecatendimentos;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpeccidadaos;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecagenda;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecvacinacao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecterritorio;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecregistros;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecacompanhamento;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecrelatorios;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpectransmissao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecconfiguracao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean gpecappacs;

	public Servidor2() {
		super();
		iniciarvariavel();
	}

	public void iniciarvariavel() {
		idservidor2 = 0;
		matricula = "";
		nome = "";
		tel = "";
		email = "";
		unidade = "";


		// Define as permissões ADMINISTRATIVAS
		superusuario = false;
		cadsalas = false;
		cadsetores = false;
		cadunidades = false;
		cadusuarios = false;
		caddrives = false;
		auditoriaclinica = false;
		listausuarios = false;
		mapapacientes = false;
		resumopaciente = false;
		aguardandoatendimento = false;
		atendimentoperiodo = false;
		biometria = false;
		comunicacaoespecialistas = false;
		correcaobam = false;
		enfermariaseleitos = false;
		imprimirbam = false;
		limparpainel = false;
		pacientesematendimento = false;
		avaliacaopacienteia_adm = false;
		pendenciaassinatura = false;
		historicocompleto_adm = false;
		prontuariocompleto = false;
		comoestaopaciente = false;
		verificarassinatura = false;
		ragprontuario = false;
		resumoclinicoia = false;
		personalizacaovisual = false;
		pacs = false;
		pcentrocirurgico = false;
		pcirurgiaagendar = false;
		pcirurgiaexecutar = false;
		pcirurgiadescrever = false;
		pcirurgiaanestesia = false;
		pcirurgiasuspender = false;
		pcirurgiaopme = false;
		pcirurgiasalas = false;
		pcirurgialistaespera = false;
		papoioassistencial = false;
		pcme = false;
		phemoterapia = false;
		psnd = false;
		prnds = false;
		pnsp = false;
		pnspgestao = false;
		pnspclassificar = false;
		pnspbloqueado = false;
		pfhirsumarioalta = false;
		logs = false;
		vigilanciaepidemiologica = false;
		configuracaovigilanciaepidemiologica = false;
		producaomedica = false;
		migracaodados = false;
		ultrassonografia = false;

		// Define as permissões NIR
		cadleitos = false;
		cadsetor = false;
		restaurarpaciente = false;
		biexecutivo = false;
		nirrestaurarinternacao = false;
		ged = false;
		gedremover = false;
		gedconsulta = false;
		cautela = false;
		nir = false;
		painelnir = false;
		localcenso = false;
		pesquisanir = false;
		nirrelatorio = false;
		setorcenso = false;
		internadosvisitaimp = false;

		// Define as permissões Enfermagem
		balancohidrico = false;
		ccih = false;
		driveenfermagem = false;
		escalaenfermagem = false;
		evolucaomultidisciplinar = false;
		pedidosexame = false;
		minhasassinaturas = false;
		avaliacaopacienteia = false;
		bulario = false;
		bulariomedico = false;
		ciclomedicacao = false;
		historicocompleto = false;
		anotacoesenfermagem = false;
		saeenfermagem = false;
		controleassistencial = false;
		sinan = false;
		documentosassistenciais = false;
		painelenfermagem = false;
		painelspa = false;
		painelmaternidade = false;
		painelpediatria = false;
		modeloevolucaoenf = false;
		modeloprescricaoenf = false;
		resumopacientes = false;
		consultorioadmin = false;
		consultoriorecepcao = false;
		consultoriomedico = false;
		consultoriofinanceiro = false;
		chatprofissionais = false;

		// Define as permissões Monitoramento e RH
		monitoramento = false;
		controleplantoes = false;
		produtividaderh = false;
		treinamentocertificacao = false;
		rh = false;
		gerenciarcertificados = false;

	}

}
