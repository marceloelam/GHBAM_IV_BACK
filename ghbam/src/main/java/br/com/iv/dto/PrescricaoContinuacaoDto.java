package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class PrescricaoContinuacaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(columnDefinition = "TEXT")
	private String observacao;
	@Column(columnDefinition = "TEXT")
	private String observacao2;
	@Column(length = 10)
	private double padiast;
	private double pasist;
	private double fc;
	private double tax;
	private double fr;
	private double spo;
	private double peso;
	private double peso2;
	
	private int bam;
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 19)
	private String localsolicitante;
	@Column(length = 55)
	private String enfermeiro;
	@Column(length = 55)
	private String tecnico;
	private boolean feito;
	private boolean checado;
	private int idprescricao;
	private String uuid; // UUID para controle frontend
}
