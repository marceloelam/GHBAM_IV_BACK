package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "protocolo")
public class Protocolo extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(length = 100, nullable = false)
	private String nome;

	@Column(length = 50)
	private String especialidade;

	@Column(length = 200)
	private String descricao;

	@Column(length = 20)
	private String versao;

	@Column(columnDefinition = "TEXT")
	private String perguntasJson;

	@Column(columnDefinition = "TEXT")
	private String camposJson;

	@Column(columnDefinition = "TEXT")
	private String regrasJson;

	@Column(nullable = false)
	private LocalDateTime dataCriacao;

	@Column
	private LocalDateTime dataAtualizacao;

	@Column(length = 50)
	private String criadoPor;

	@Column(length = 50)
	private String atualizadoPor;

	private boolean ativo;

	public Protocolo() {
		super();
		this.dataCriacao = LocalDateTime.now();
		this.ativo = true;
		this.versao = "1.0";
	}
}
