package br.com.iv.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class ReceitaMedicamentoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idreceitamedicamento;
	private int nbam;
	private String remedio;
	private String complemento;
	private String qtde;
	private String tipo;
	private String tempo;
	private String durante;
	private String periodo;
	private String observacao;
	private String medicocrm;
	private String dataehora;
}
