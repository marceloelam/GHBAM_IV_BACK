package br.com.iv.dto;

import lombok.Data;

@Data
public class MultidisciplinarEncaminhamentoDto {

	private int idpaciente;
	private Integer idAtendimentoOrigem;
	private String categoriaOrigem;
	private String categoriaDestino;
	private String profissionalDestino;
	private String unidade;
	private String setor;
	private String motivo;
	private String prioridade;
	private String observacao;
	private String status;

}
