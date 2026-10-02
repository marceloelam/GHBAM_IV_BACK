package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "unidade_videos")
public class UnidadeVideo extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

	@Column(length = 15, nullable = false)
	private String sigla;

	@Column(length = 120)
	private String titulo;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String url;

	@Column(columnDefinition = "integer default 0")
	private int ordem;

	@Column(columnDefinition = "boolean default true")
	private boolean ativo;

	@Column(length = 20, columnDefinition = "varchar(20) default 'desconhecido'")
	private String statusVerificacao;

	private LocalDateTime verificadoEm;

	@Column(updatable = false)
	private LocalDateTime criadoEm;

	public UnidadeVideo() {
		this.ordem = 0;
		this.ativo = true;
		this.statusVerificacao = "desconhecido";
	}

	@jakarta.persistence.PrePersist
	public void prePersist() {
		if (this.criadoEm == null) {
			this.criadoEm = LocalDateTime.now();
		}
	}
}
