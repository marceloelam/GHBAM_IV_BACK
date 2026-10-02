package br.com.iv.dto;

import lombok.Data;

@Data
public class VacinaPediatricaDto {

	private int idpaciente;
	private String vacina;
	private String dose;
	private String dataAplicacao;
	private String idadeAplicacao;
	private String observacao;
	private String medico;
	private String crm;

}
