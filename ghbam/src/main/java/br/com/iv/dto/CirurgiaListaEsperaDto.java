package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class CirurgiaListaEsperaDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idpaciente;
	private String nomepaciente;
	private String cnspaciente;
	private String unidade;
	private String especialidade;
	private String procedimentoCodigo;
	private String procedimentoDescricao;
	private String prioridade;
	private String justificativaPrioridade;
	private String dataPrevista;
	private String observacao;
}
