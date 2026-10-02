package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "VacinaPediatrica")
public class VacinaPediatrica extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

	private int idpaciente;
	@Column(length = 60)
	private String vacina;
	@Column(length = 30)
	private String dose;

	@Temporal(TemporalType.DATE)
	private Date dataAplicacao;
	@Column(length = 15)
	private String idadeAplicacao;
	@Column(length = 4000)
	private String observacao;

	@Column(length = 120)
	private String medico;
	@Column(length = 15)
	private String crm;
	@Column(length = 40)
	private String criadoPorMatricula;

	@Temporal(TemporalType.TIMESTAMP)
	private Date criadoEm;
	@Temporal(TemporalType.TIMESTAMP)
	private Date atualizadoEm;

	public VacinaPediatrica() {
		super();
	}

	@PrePersist
	protected void aoPersistir() {
		if (criadoEm == null) {
			criadoEm = new Date();
		}
		atualizadoEm = new Date();
	}

	@PreUpdate
	protected void aoAtualizar() {
		atualizadoEm = new Date();
	}

}
