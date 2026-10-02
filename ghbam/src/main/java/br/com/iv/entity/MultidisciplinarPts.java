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
@Entity(name = "MultidisciplinarPts")
public class MultidisciplinarPts extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

	private int idpaciente;
	private Integer idAtendimento;

	@Column(length = 2000)
	private String problema;
	@Column(length = 2000)
	private String objetivo;
	@Column(length = 2000)
	private String acao;
	@Column(length = 120)
	private String profissional;
	@Column(length = 40)
	private String categoriaProfissional;
	@Column(length = 40)
	private String matriculaResponsavel;

	@Temporal(TemporalType.DATE)
	private Date dataInicio;
	@Column(length = 60)
	private String prazo;
	@Column(length = 20)
	private String status = "PLANEJADO";
	@Column(length = 2000)
	private String resultadoEsperado;
	@Column(length = 2000)
	private String resultadoAlcancado;
	@Column(length = 2000)
	private String observacao;
	@Temporal(TemporalType.DATE)
	private Date dataConclusao;

	@Column(length = 40)
	private String criadoPorMatricula;
	@Temporal(TemporalType.TIMESTAMP)
	private Date criadoEm;
	@Temporal(TemporalType.TIMESTAMP)
	private Date atualizadoEm;

	public MultidisciplinarPts() {
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
