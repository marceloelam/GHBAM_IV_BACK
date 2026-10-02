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
@Table(name = "cirurgia_sala")
public class CirurgiaSala extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idsala;

	@Column(length = 60, nullable = false)
	private String nome;

	@Column(length = 25, nullable = false)
	private String unidade;

	@Column(length = 15)
	private String cnes;

	@Column(length = 200)
	private String especialidades;

	@Column(name = "hora_inicio_expediente", length = 5)
	private String horaInicioExpediente;

	@Column(name = "hora_fim_expediente", length = 5)
	private String horaFimExpediente;

	@Column(name = "tempo_limpeza_min", nullable = false)
	private int tempoLimpezaMin;

	@Column(nullable = false)
	private boolean ativa;

	@Column(length = 200)
	private String observacao;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "usuario_criador", length = 40)
	private String usuarioCriador;

	public CirurgiaSala() {
		super();
		this.criadoEm = LocalDateTime.now();
		this.ativa = true;
		this.tempoLimpezaMin = 30;
		this.horaInicioExpediente = "07:00";
		this.horaFimExpediente = "19:00";
	}
}
