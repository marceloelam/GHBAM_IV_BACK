package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class CboDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 10)
	private String codigo;
	@Column(length = 150)
	private String titulo;
}
