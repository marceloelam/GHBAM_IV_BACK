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
@Table(name = "cirurgia_equipe")
public class CirurgiaEquipe extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idequipe;

	@Column(nullable = false)
	private int idcirurgia;

	@Column
	private Integer idservidor;

	@Column(length = 15)
	private String matricula;

	@Column(length = 80, nullable = false)
	private String nome;

	@Column(length = 30, nullable = false)
	private String funcao;

	@Column(length = 20)
	private String conselho;

	@Column(length = 10)
	private String cbo;

	@Column(nullable = false)
	private boolean presente;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "usuario_criador", length = 40)
	private String usuarioCriador;

	public CirurgiaEquipe() {
		super();
		this.criadoEm = LocalDateTime.now();
	}
}
