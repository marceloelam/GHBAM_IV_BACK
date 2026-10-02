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
@Table(name = "cirurgia_ficha_anestesica")
public class CirurgiaFichaAnestesica extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idficha;

	@Column(nullable = false, unique = true)
	private int idcirurgia;

	@Column
	private Integer idanestesista;

	@Column(length = 80)
	private String nomeanestesista;

	@Column(length = 50)
	private String tecnica;

	@Column(length = 5)
	private String asa;

	@Column(length = 5)
	private String mallampati;

	@Column(name = "jejum_horas")
	private Integer jejumHoras;

	@Column(name = "via_aerea", length = 60)
	private String viaAerea;

	@Column(name = "acesso_venoso", length = 100)
	private String acessoVenoso;

	@Column(length = 200)
	private String monitorizacao;

	@Column(name = "drogas_json", columnDefinition = "TEXT")
	private String drogasJson;

	@Column(name = "sinais_json", columnDefinition = "TEXT")
	private String sinaisJson;

	@Column(columnDefinition = "TEXT")
	private String intercorrencias;

	@Column
	private Integer aldrete;

	@Column(name = "evolucao_rpa", columnDefinition = "TEXT")
	private String evolucaoRpa;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em")
	private LocalDateTime atualizadoEm;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaFichaAnestesica() {
		super();
		this.criadoEm = LocalDateTime.now();
	}
}
