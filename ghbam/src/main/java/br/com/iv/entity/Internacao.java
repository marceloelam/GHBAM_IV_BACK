package br.com.iv.entity;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
@Data
@Entity(name = "Internacao")
public class Internacao   extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L; // = 8095204806563888562L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idninternado;
	@Column(length = 50)
	private String procedencia;
	private LocalDate datainternacao;
	@Column(length = 20)
	private String prontuario;
	@Column(length = 20)
	private String tpdocumento;
	@Column(length = 20)
	private String ndocumento;
	@Column(length = 20)
	private String bam;
	@Column(length = 20)
	private String cid;
	@Column(length = 40)
	private String local;
	@Column(length = 55)
	private String medicoqueinternou;
	private LocalDate datasaida;
	@Column(length = 55)
	private String motivodaalta;
	@Column(length = 55)
	private String medicodeualta;
	@Column(length = 410)
	private String obs;
	@Column(length = 55)
	private String atendente;
	@Column(length = 10)
	private String origem;
	@Column(length = 25)
	private String unidade;
	private int idDoPaciente;

	
	public Internacao() throws ParseException {
		super();
		prontuario = "";
		local = "TRAUMA";
		procedencia = "TRAUMA";
		tpdocumento = "URGENCIA";
		cid = "";
		unidade = "";
		medicoqueinternou = "";
		medicodeualta = "";
		motivodaalta = "-";
		obs = "";
		atendente = "";
		origem = "INTERNADO";
		idDoPaciente = 0;

		ndocumento = montarInternado();

		LocalDate hoje = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd"); 
		String dataFormatada = hoje.format(formatter);
		//LocalDate dia = hoje.plusDays(0); //amanhã 1 
		//String amanhaFormatada = dia.format(formatter);
		LocalDate data = LocalDate.parse(dataFormatada, formatter);
		
		datainternacao = data;
		//datasaida = data;

	}

	public String montarInternado() {
		Date dataatual = new Date();
		SimpleDateFormat formata = new SimpleDateFormat("dd/MM/yyyy");
		String dt_hoje = formata.format(dataatual);
		String datatual = dt_hoje.replaceAll("-", "/");
		String[] k = datatual.split("/");
		String ano = k[2];
		String mes = k[1];
		String dia = k[0];

		// hora atual sem :
		Calendar data = Calendar.getInstance();
		int hora = data.get(Calendar.HOUR_OF_DAY);
		int min = data.get(Calendar.MINUTE);
		int seg = data.get(Calendar.SECOND);

		int anoint = Integer.parseInt(ano);
		int mesint = Integer.parseInt(mes);
		int diaint = Integer.parseInt(dia);

		String anoInteiro = Integer.toString(anoint);
		anoInteiro = ano.substring(2, 4);

		String internado = anoInteiro + Integer.toString(mesint) + Integer.toString(diaint) + Integer.toString(hora)
				+ Integer.toString(min) + Integer.toString(seg);
		return internado;

	}

}
