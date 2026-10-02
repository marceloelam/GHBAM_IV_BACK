package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@SuppressWarnings("unused")
@Data
@Entity(name = "prescricaocont")
public class PrescricaoContinuacao  extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 6815899332074055395L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idprescricaocontinuacao;
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
	@Column(length = 95)
	private String enfermeiro;
	@Column(length = 95)
	private String tecnico;
	private boolean feito;
	private boolean checado;
	private int idprescricao;
	private String uuid; // UUID para controle frontend
	@Column(length = 30)
	private String criadopor; // matricula do profissional que registrou

	
	public PrescricaoContinuacao() {
		super();
		// this.idprescricaocontinuacao = idprescricaocontinuacao;
		this.observacao = "";
		this.observacao2 = "";
		this.padiast = 0.0;
		this.pasist = 0.0;
		this.fc = 0.0;
		this.peso = 0.0;
		this.peso2 = 0.0;
		this.tax = 0.0;
		this.fr = 0.0;
		this.spo = 0.0;
		this.bam = 0;
		this.data = "";
		this.hora = "";
		this.localsolicitante = "";
		this.enfermeiro = "";
		this.tecnico = "";
		this.feito = false;
		this.checado = false;
		this.uuid = "";
		this.criadopor = "";
	}

}
