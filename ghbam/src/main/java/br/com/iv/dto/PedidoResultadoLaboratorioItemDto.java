package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

// Exame reconhecido no PDF do laudo; codigoLoinc vazio = nao reconhecido pelo dicionario.
@Data
public class PedidoResultadoLaboratorioItemDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private String nomeExame;
	private String codigoLoinc;
	private String valor;
	private String unidadeMedida;
	private String referenciaValor;
}
