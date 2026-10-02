package br.com.iv.entity;

import java.io.Serializable;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity(name = "ResumoDeAlta")
public class ResumoDeAlta extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idResumoalta;
	@Column(length = 20, nullable = false)
	private String status = "ATIVO";
	@Column(name = "cancelado_em")
	private LocalDateTime canceladoEm;
	@Column(name = "cancelado_por", length = 120)
	private String canceladoPor;
	@Column(name = "motivo_cancelamento", length = 500)
	private String motivoCancelamento;
	@Column(length = 55)
	private String nome;
	@Column(length = 15)
	private String prontuario;
	@Column(length = 15)
	private String datainternacao;
	private LocalDate dataalta;
	private int internacao;
	private int objetivo;
	private boolean clinicaalta;
	@Column(length = 100)
	private String especificarclicica;
	@Column(length = 100)
	private String diaginicial;
	@Column(length = 15)
	private String cid;
	@Column(length = 100)
	private String diagfinal;
	@Column(length = 15)
	private String cid2;
	private boolean endoscopiadiges;
	private boolean tomografiacom;
	private boolean ultrasono;
	private boolean sanguetot;
	private boolean obito;
	private boolean concentradohema;
	private boolean concentradounidades;
	private boolean plasmahumano;
	private boolean plasmahumanounida;
	private boolean plaquetas;
	private boolean plaquetasunidades;
	private boolean albumina;
	private boolean albuminaunidades;
	private boolean puncao;
	@Column(length = 3)
	private String ndiaspuncao;
	private boolean puncaotora;
	private boolean entubacao;
	private boolean cateterismo;
	@Column(length = 3)
	private String ndiascateterismo;
	private boolean cti;
	@Column(length = 3)
	private String ndiascti;
	@Column(length = 3)
	private String ndiasentoro;
	private boolean procedimentocir;
	@Column(length = 20)
	private String ctiperiodo;
	private boolean infeccao;
	@Column(length = 100)
	private String mateenvia;
	private boolean cultura;
	@Column(length = 100)
	private String germesiso;
	@Column(length = 100)
	private String antibioticoutil;
	@Column(length = 20)
	private String periodo;
	private boolean caracteristicapropria;
	private boolean motivosocial;
	private boolean intercorrencia;
	private boolean doencacronica;
	private boolean outros;
	private LocalDate dataobito;
	private String horaobito;
	private boolean semtra;
	private boolean nodecurs;
	private boolean menos;
	private boolean mais;
	@Column(length = 100)
	private String causamorte;
	private boolean complic;
	@Column(length = 100)
	private String especificarcomplicacao;
	private int tipocondicao;
	private boolean administrativa;
	private boolean decisaomedica;
	private boolean apedido;
	private boolean indisciplina;
	private boolean transparencia;
	private boolean outro;
	private boolean encaamb;
	@Column(length = 100)
	private String especificaambula;
	private int internado;
	@Column(length = 100)
	private String especperiodo;
	@Column(length = 15)
	private String bam;
	private boolean sangueunida;
	private int ndiapuncaotora;
	private boolean clinicadealta;
	private boolean admissao;
	@Column(length = 100)
	private String procedimentocirespecifi;
	@Column(length = 45)
	private String tipocondicaotrans;
	@Column(columnDefinition = "TEXT")
	private String relato;
	@Column(name = "plano_cuidados", columnDefinition = "TEXT")
	private String planoCuidados;
	@Column(name = "capacidade_funcional", columnDefinition = "TEXT")
	private String capacidadeFuncional;

	public ResumoDeAlta() {
		super();
		limpar();

	}

	public void limpar() {
		idResumoalta = 0;
		nome = "";
		prontuario = "";
		internacao = 1;
		objetivo = 1;
		clinicaalta = false;
		especificarclicica = "";
		diaginicial = "";
		cid = "";
		diagfinal = "";
		cid2 = "";
		endoscopiadiges = false;
		tomografiacom = false;
		ultrasono = false;
		sanguetot = false;
		concentradohema = false;
		concentradounidades = false;
		plasmahumano = false;
		plasmahumanounida = false;
		plaquetas = false;
		plaquetasunidades = false;
		albumina = false;
		albuminaunidades = false;
		puncao = false;
		ndiaspuncao = "";
		ndiasentoro = "";
		puncaotora = false;
		entubacao = false;
		cateterismo = false;
		ndiascateterismo = "";
		cti = false;
		ndiascti = "";
		procedimentocir = false;
		ctiperiodo = "";
		infeccao = false;
		mateenvia = "";
		cultura = false;
		germesiso = "";
		antibioticoutil = "";
		periodo = "";
		caracteristicapropria = false;
		motivosocial = false;
		intercorrencia = false;
		doencacronica = false;
		outros = false;
		semtra = false;
		nodecurs = false;
		menos = false;
		mais = false;
		causamorte = "";
		complic = false;
		especificarcomplicacao = "";
		tipocondicao = 1;
		administrativa = false;
		decisaomedica = false;
		apedido = false;
		indisciplina = false;
		transparencia = false;
		encaamb = false;
		especificaambula = "";
		internado = 0;
		especperiodo = "";
		bam = "";
		obito = false;
		sangueunida = false;
		ndiapuncaotora = 0;
		clinicadealta = false;
		admissao = false;
		procedimentocirespecifi = "";
		tipocondicaotrans = "";
		relato = "";

		Date data3 = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("dd/MM/yyyy");
		SimpleDateFormat formatadorH = new SimpleDateFormat("HH:mm");

		String dat1 = formatador.format(data3);
		String dat2 = formatadorH.format(data3);
		LocalDate hoje = LocalDate.now();

		dataobito = hoje;
		horaobito = dat2;
		datainternacao = dat1;
		dataalta = hoje;
	}

}
