package br.com.iv.entity;

import java.io.Serializable;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity(name = "unidade_video_canal")
@Table(name = "unidade_video_canal")
public class UnidadeVideoCanal extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 15)
	private String sigla;

	@Column(nullable = false, length = 160)
	private String nome;

	@Column(length = 160)
	private String descricao;

	@Column(columnDefinition = "TEXT")
	private String logo;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String url;

	@Column(nullable = false)
	private int ordem;
}
