package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "unidade_video_config")
public class UnidadeVideoConfig extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(length = 15)
	private String sigla;

	@Column(length = 20)
	private String tipoTransicao;

	private double duracaoTransicao;

	private boolean mudo;

	private int volume;

	@Column(length = 10)
	private String modo;

	@Column(columnDefinition = "TEXT")
	private String urlM3u;

	@Column(length = 120)
	private String tituloM3u;

	public UnidadeVideoConfig() {
		this.tipoTransicao = "corte";
		this.duracaoTransicao = 1.0;
		this.mudo = false;
		this.volume = 80;
		this.modo = "video";
	}
}
