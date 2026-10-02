package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class CirurgiaProcedimentoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private String codigo;
	private String descricao;
	private String tabela;
	private Boolean principal;
	private Integer quantidade;
	private String lateralidade;
	private Boolean realizado;
}
