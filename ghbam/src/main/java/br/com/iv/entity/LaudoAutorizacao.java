package br.com.iv.entity;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
@Data
@Entity(name = "LaudoAutorizacao")
public class LaudoAutorizacao extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 6216918689717165686L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idlaudo;
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

	// Getters e Setters

	public LaudoAutorizacao() {
		super();
		limpar();
	}

	public void limpar() {

		cnes1 = "";
		solicitante = "";

		executante = "";
		cnes2 = "";
		bam = 0;
		sintomasclinicos = "";
		jusinternacao = "";
		provasdiag = "";
		diagnosticoinicial = "";
		cidprincipal = "";
		cidsecundario = "";
		cidcausas = "";
		procedimentosolicitado = "";
		codprocedimento = "";
		clinica = "";
		caraterinternacao = "";
		documento1 = 2;
		cpfprofissional = "";
		solicitanteassistente = "";
		documento2 = 4;
		cnpjsegu = "";
		nbilhete = "";
		serie = "";
		cnpjempresa = "";
		cnaeempresa = "";
		cbor = "";
		tipoacidente = 6;
		profissionalautorizador = "";
		documento3 = 2;
		cpfprofautorizador = "";
		nainternacao = "";
		unidade = "";

		try {
			Date data3 = new Date();
			Calendar cal = Calendar.getInstance();
			cal.setTime(data3);
			cal.add(Calendar.DATE, 0);
			SimpleDateFormat format1 = new SimpleDateFormat("dd/MM/yyyy");
			String dat1 = format1.format(cal.getTime());
			// SimpleDateFormat formatador = new SimpleDateFormat("dd/MM/yyyy");
			// String dat1 = formatador.format(data3);
			Date dataFormatada = format1.parse(dat1);
			dataautorizacao = dataFormatada;
			datasolicitacao = dataFormatada;

		} catch (ParseException e) {
			// e.printStackTrace();
		}

	}

}
