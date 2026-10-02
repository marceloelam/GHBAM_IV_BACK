package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class UnidadeVideoConfigDto implements Serializable {
	private static final long serialVersionUID = 1L;

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
}
