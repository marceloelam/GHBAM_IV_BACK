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
@Table(name = "cirurgia_checklist")
public class CirurgiaChecklist extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idchecklist;

	@Column(nullable = false)
	private int idcirurgia;

	@Column(length = 20, nullable = false)
	private String momento;

	@Column(name = "respostas_json", columnDefinition = "TEXT")
	private String respostasJson;

	@Column(nullable = false)
	private boolean conforme;

	@Column(length = 300)
	private String pendencias;

	@Column(length = 80)
	private String responsavel;

	@Column(name = "registrado_em", nullable = false)
	private LocalDateTime registradoEm;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaChecklist() {
		super();
		this.registradoEm = LocalDateTime.now();
	}
}
