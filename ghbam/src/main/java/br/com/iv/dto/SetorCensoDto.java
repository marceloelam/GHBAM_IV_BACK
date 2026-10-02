package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class SetorCensoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idsetorcenso;
	@Column(length = 6)
	private String codigo;
	@Column(length = 30)
	private String setor;
	@Column(length = 35)
	private String unidade;
	private boolean excluido;
}
