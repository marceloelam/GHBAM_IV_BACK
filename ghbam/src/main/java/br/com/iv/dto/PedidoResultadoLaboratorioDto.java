package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class PedidoResultadoLaboratorioDto implements Serializable {

	private static final long serialVersionUID = 1L; // = 344185911652747414L;
	
	private int idPedidoResultado;
	@Column(length = 65) 
	private String medico;
	@Column(length = 25) 
	private String crm;
	@Column(length = 25) 
	private String setor;
	@Column(length = 25) 
	private String local;
	private int bam;
	@Column(columnDefinition = "TEXT")
	private String arquivo;
	@Column(length = 25)
	private String unidade;
	@Column(length = 35)
	private String descricao;
	@Column(length = 10000)
	private String grupopedidos;
	private String valorEstruturado;
	private String unidadeMedida;
	private String referenciaValor;
	private String amostraBiologica;
	private String dataColeta;
	private String codigoLoinc;
	private String justificativaAusenciaValor;
	// Exames lidos do PDF (V189); null = manter os itens atuais.
	private java.util.List<PedidoResultadoLaboratorioItemDto> itens;
	// Lidos do PDF so para a vigilancia (V242): nao entram nos itens nem na fila RNDS.
	private java.util.List<java.util.Map<String, String>> resultadosInfecciosos;

}
