package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class SetoresUnidadesDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 20)
	private String siglaunidade;
	@Column(length = 20)
	private String setor;
	private boolean triar;
	private String hospital;
}
