package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class ResumoDeAltaDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idResumoalta;
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
	@Column(columnDefinition = "TEXT")
	private String planoCuidados;
	@Column(columnDefinition = "TEXT")
	private String capacidadeFuncional;
}
