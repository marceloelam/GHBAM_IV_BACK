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
@Entity(name = "MultidisciplinarEncaminhamento")
public class MultidisciplinarEncaminhamento extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

	private int idpaciente;
	private Integer idAtendimentoOrigem;

	@Column(length = 120)
	private String profissionalSolicitante;
	@Column(length = 40)
	private String matriculaSolicitante;
	@Column(length = 40)
	private String categoriaOrigem;
	@Column(length = 40)
	private String categoriaDestino;
	@Column(length = 120)
	private String profissionalDestino;

	@Column(length = 20)
	private String unidade;
	@Column(length = 60)
	private String setor;
	@Column(length = 2000)
	private String motivo;
	@Column(length = 15)
	private String prioridade;
	@Column(length = 2000)
	private String observacao;
	@Column(length = 20)
	private String status = "SOLICITADO";

	@Temporal(TemporalType.TIMESTAMP)
	private Date dataSolicitacao;
	@Column(length = 40)
	private String criadoPorMatricula;
	@Temporal(TemporalType.TIMESTAMP)
	private Date criadoEm;
	@Temporal(TemporalType.TIMESTAMP)
	private Date atualizadoEm;

	public MultidisciplinarEncaminhamento() {
		super();
	}

	@PrePersist
	protected void aoPersistir() {
		if (criadoEm == null) {
			criadoEm = new Date();
		}
		if (dataSolicitacao == null) {
			dataSolicitacao = new Date();
		}
		atualizadoEm = new Date();
	}

	@PreUpdate
	protected void aoAtualizar() {
		atualizadoEm = new Date();
	}

}
