package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "avaliacao")
public class Avaliacao  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idavaliacao;
	private int idmedico;
	@Column(length = 8000)
	private String anamnese;
	private boolean alergiaSouN;
	private double padiast;
	private double pasist;
	private double fc;
	private double tax;
	private double fr;
	private double spo;
	private double peso;
	@Column(nullable = false, columnDefinition = "double precision DEFAULT 0.0")
	private double peso2;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean eupneico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean dispneico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean taquipneico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean acianotico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean cianotico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean hidratado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean desidratado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean normocorado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean hipocorado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean sudoreico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean anicterico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean icterico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean pulsocheio;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean pulsofino;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean etilismo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean acordado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean orientado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean desorientado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean torporoso;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean comatoso;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean isocorico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean anisocorico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean midriatico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean miotico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean rigideznuca;
	@Column(length = 100)
	private String cabeca;
	private double abdome;
	private double torax;
	private double membro;
	@Column(length = 10)
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 40)
	private String controle;
	private int avaliado;
	private int esperando;
	private int idpaciente;
	private int cor;
	@Transient
	private int total;
	@Column(length = 2000)
	private String comentario;
	@Column(columnDefinition = "TEXT")
	private String hpp;
	@Column(length = 10)
	private String risco;
	@Column(length = 20)
	private String unidade;
	private int glasgow;
	private int niveldor;

	public Avaliacao() {
		super();
		iniciarAtributos();
	}

	public void iniciarAtributos() {
		glasgow = 15;
		niveldor = 0;
		idmedico = 1;
		anamnese = ".";
		padiast = 0.0;
		pasist = 0.0;
		fc = 0.0;
		tax = 0.0;
		fr = 0.0;
		peso = 0.0;
		peso2 = 0.0;
		spo = 0.0;
		eupneico = false;
		dispneico = false;
		taquipneico = false;
		acianotico = false;
		hidratado = false;
		desidratado = false;
		normocorado = false;
		hipocorado = false;
		sudoreico = false;
		anicterico = false;
		icterico = false;
		pulsocheio = false;
		pulsofino = false;
		etilismo = false;
		acordado = false;
		orientado = false;
		desorientado = false;
		torporoso = false;
		comatoso = false;
		isocorico = false;
		anisocorico = false;
		midriatico = false;
		miotico = false;
		rigideznuca = false;
		cabeca = "";
		abdome = 0.0;
		membro = 0.0;
		//data = converteData();
		hora = converteTime();
		controle = "";
		avaliado = 0;
		esperando = 0;
		cor = 1;
		comentario = ".";
		hpp = "";
		risco = "AZUL";
		alergiaSouN = false;
		unidade = "";

	}

	protected String converteData() {
		LocalDate dataAtual = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String dataFormatada = dataAtual.format(formatador);

		return dataFormatada;
	}

	protected String converteTime() {
		LocalTime horaAtual = LocalTime.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("HH:mm");
		String timeFormatada = horaAtual.format(formatador);

		return timeFormatada;
	}


	
	

}
