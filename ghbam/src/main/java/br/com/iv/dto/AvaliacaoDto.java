package br.com.iv.dto;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;

import lombok.Data;



@Data
public class AvaliacaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

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
	private double peso2;
	private boolean eupneico;
	private boolean dispneico;
	private boolean taquipneico;
	private boolean acianotico;
	private boolean cianotico;
	private boolean hidratado;
	private boolean desidratado;
	private boolean normocorado;
	private boolean hipocorado;
	private boolean sudoreico;
	private boolean anicterico;
	private boolean icterico;
	private boolean pulsocheio;
	private boolean pulsofino;
	private boolean etilismo;
	private boolean acordado;
	private boolean orientado;
	private boolean desorientado;
	private boolean torporoso;
	private boolean comatoso;
	private boolean isocorico;
	private boolean anisocorico;
	private boolean midriatico;
	private boolean miotico;
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
	private int idpaci;
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
}
