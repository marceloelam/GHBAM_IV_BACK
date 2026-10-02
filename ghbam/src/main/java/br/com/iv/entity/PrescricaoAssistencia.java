package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
@Data
@SuppressWarnings("unused")
@Entity(name = "prescricaoassist")
public class PrescricaoAssistencia  extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L; // = -4218034887480105573L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idprescricaoassistencia;
	@Column(columnDefinition = "TEXT")
	private String observacaoassist;
	private int bam;
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 19)
	private String localsolicitante;
	@Column(length = 55)
	private String tecnico;

	
	public PrescricaoAssistencia() {
		super();

		this.observacaoassist = "";
		this.bam = 0;
		this.data = "";
		this.hora = "";
		this.localsolicitante = "";
		this.tecnico = "";
	}

}
