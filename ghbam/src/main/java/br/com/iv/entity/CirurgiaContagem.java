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
@Table(name = "cirurgia_contagem")
public class CirurgiaContagem extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idcontagem;

	@Column(nullable = false)
	private int idcirurgia;

	@Column(length = 30, nullable = false)
	private String tipo;

	@Column(length = 20, nullable = false)
	private String momento;

	@Column(nullable = false)
	private int quantidade;

	@Column(nullable = false)
	private boolean conforme;

	@Column(length = 300)
	private String divergencia;

	@Column(length = 80)
	private String conferente;

	@Column(name = "segundo_conferente", length = 80)
	private String segundoConferente;

	@Column(name = "registrado_em", nullable = false)
	private LocalDateTime registradoEm;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaContagem() {
		super();
		this.registradoEm = LocalDateTime.now();
		this.conforme = true;
	}
}
