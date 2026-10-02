package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class CirurgiaEquipeDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer idservidor;
	private String matricula;
	private String nome;
	private String funcao;
	private String conselho;
	private String cbo;
	private Boolean presente;
}
