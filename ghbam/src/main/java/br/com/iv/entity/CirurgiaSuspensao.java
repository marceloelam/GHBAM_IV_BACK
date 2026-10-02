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
@Table(name = "cirurgia_suspensao")
public class CirurgiaSuspensao extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idsuspensao;

	@Column(nullable = false)
	private int idcirurgia;

	@Column(length = 40, nullable = false)
	private String categoria;

	@Column(length = 300, nullable = false)
	private String motivo;

	@Column(name = "suspensa_em", nullable = false)
	private LocalDateTime suspensaEm;

	@Column(length = 80)
	private String responsavel;

	@Column(nullable = false)
	private boolean remarcada;

	@Column(name = "idcirurgia_remarcada")
	private Integer idcirurgiaRemarcada;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaSuspensao() {
		super();
		this.suspensaEm = LocalDateTime.now();
	}
}
