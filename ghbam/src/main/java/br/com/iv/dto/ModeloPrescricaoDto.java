package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class ModeloPrescricaoDto implements Serializable {
	private static final long serialVersionUID = 1L;
	@Column(columnDefinition = "TEXT")
	private String modelo;
	@Column(length = 45)
	private String titulo;
	@Column(length = 85)
	private String unidade;
	@Column(length = 45)
	private String sigla;
	@Column(length = 115)
	private String criador;
}

