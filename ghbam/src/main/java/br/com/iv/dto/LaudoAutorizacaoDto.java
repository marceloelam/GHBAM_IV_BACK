package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.Data;

@Data
public class LaudoAutorizacaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 100)
	private String solicitante;
	@Column(length = 100)
	private String cnes1;
	@Column(length = 100)
	private String executante;
	@Column(length = 100)
	private String cnes2;
	private int bam;
	@Column(length = 600)
	private String sintomasclinicos;
	@Column(length = 600)
	private String jusinternacao;
	@Column(length = 600)
	private String provasdiag;
	@Column(length = 100)
	private String diagnosticoinicial;
	@Column(length = 100)
	private String cidprincipal;
	@Column(length = 100)
	private String cidsecundario;
	@Column(length = 100)
	private String cidcausas;
	@Column(length = 140)
	private String procedimentosolicitado;
	@Column(length = 100)
	private String codprocedimento;
	@Column(length = 100)
	private String clinica;
	@Column(length = 100)
	private String caraterinternacao;
	private int documento1;
	@Column(length = 100)
	private String cpfprofissional;
	@Column(length = 100)
	private String solicitanteassistente;
	@Temporal(TemporalType.DATE)
	private Date datasolicitacao;
	private int documento2;
	@Column(length = 100)
	private String cnpjsegu;
	@Column(length = 100)
	private String nbilhete;
	@Column(length = 100)
	private String serie;
	@Column(length = 100)
	private String cnpjempresa;
	@Column(length = 100)
	private String cnaeempresa;
	@Column(length = 100)
	private String cbor;
	private int tipoacidente;
	@Column(length = 100)
	private String profissionalautorizador;
	private int documento3;
	@Column(length = 100)
	private String cpfprofautorizador;
	@Column(length = 100)
	private String nainternacao;
	@Temporal(TemporalType.DATE)
	private Date dataautorizacao;
	@Column(length = 25)
	private String unidade;
	private int idDoTratamento;
	private int idDoRegistro;
}
