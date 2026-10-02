package br.com.iv.dto;

import java.io.Serializable;


import lombok.Data;

@Data
public class EstoqueCentralDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idinsumo;
	private String material;
	// @Column(length = 800)
	private String descricao;
	// @Column(length = 75)
	private String fornecedor;
	// @Column(length = 15)
	private String notafiscal;
	private String tipo;
	private int quantidade;
	private double valor;
	// @Temporal(TemporalType.DATE)
	private String fabricacao; // formato yyyy/MM/dd
	private String validade;   // formato yyyy/MM/dd
	private String entrada;    // formato yyyy/MM/dd
	// @Column(length = 35)
	private String lote;
	// @Column(length = 55)
	private String cadastradopor;
	// @Column(length = 55)
	private String atualizadopor;
	// @Column(length = 20)
	private String correlato;
	// @Column(length = 20)
	private String controlado;
	// @Column(length = 20)
	private String medicamento;
	// @Column(length = 20)
	private String altavigilancia;
	// @Column(length = 20)
	private String codbarra;
	private boolean permuta;
	// @Column(length = 100)
	private String descpermuta;
	private int idzamed;
	private String unidade;
}
