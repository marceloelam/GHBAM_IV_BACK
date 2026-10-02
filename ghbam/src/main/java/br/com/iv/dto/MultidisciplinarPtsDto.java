package br.com.iv.dto;

import lombok.Data;

@Data
public class MultidisciplinarPtsDto {

	private int idpaciente;
	private Integer idAtendimento;
	private String problema;
	private String objetivo;
	private String acao;
	private String profissional;
	private String categoriaProfissional;
	private String prazo;
	private String status;
	private String resultadoEsperado;
	private String resultadoAlcancado;
	private String observacao;
	private String dataConclusao;

}
