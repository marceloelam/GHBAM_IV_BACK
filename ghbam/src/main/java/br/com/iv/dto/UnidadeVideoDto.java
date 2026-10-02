package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class UnidadeVideoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 15)
	private String sigla;
	@Column(length = 120)
	private String titulo;
	@Column(columnDefinition = "TEXT")
	private String url;
	private int ordem;
	private boolean ativo;
}
