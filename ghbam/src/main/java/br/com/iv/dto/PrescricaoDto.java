package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class PrescricaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 100)
	private String medicacao;
	@Column(columnDefinition = "TEXT")
	private String prescricao;
	@Column(columnDefinition = "TEXT")
	private String prescricaopuro;
	@Column(columnDefinition = "TEXT")
	private String observacao2;
	@Column(length = 10)
	private String tprpa;
	@Column(length = 10)
	private String cness;
	@Column(length = 80)
	private String mediccrm;
	@Column(length = 60)
	private String medicoq;
	private int bam;
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 19)
	private String localsolicitante;
	private boolean feito;
	// Aprazamento
	private String hora1;
	private boolean hora11;
	private String hora2;
	private boolean hora22;
	private String hora3;
	private boolean hora33;
	private String hora4;
	private boolean hora44;
	private String hora5;
	private boolean hora55;
	private String hora6;
	private boolean hora66;
	private String hora7;
	private boolean hora77;
	private String hora8;
	private boolean hora88;
	@Column(length = 300)
	private String medicamentopedido;
	private int retirada;
	private boolean farmfez;

	@Column(columnDefinition = "TEXT")
	private String observacao;

	@Column(columnDefinition = "TEXT")
	private String observacao3;

	@Column(length = 60)
	private String quem;

	@Column(columnDefinition = "TEXT")
	private String motivoRetificacao;

}
