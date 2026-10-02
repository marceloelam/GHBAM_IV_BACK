package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Data;

@Data
@lombok.EqualsAndHashCode(callSuper = false)
@Entity(name = "estoquecentral")
public class EstoqueCentral extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 4670044099960582681L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idinsumo;
	@Column(length = 220)
	private String material;
	@Column(length = 800)
	private String descricao;
	@Column(length = 75)
	private String fornecedor;
	@Column(length = 15)
	private String notafiscal;
	private String tipo;
	private int quantidade;
	private double valor;
	@Column(length = 12)
	private String fabricacao; // formato yyyy/MM/dd
	@Column(length = 12)
	private String validade; // formato yyyy/MM/dd
	@Column(length = 12)
	private String entrada; // formato yyyy/MM/dd
	@Column(length = 35)
	private String lote;
	@Column(length = 55)
	private String cadastradopor;
	@Column(length = 55)
	private String atualizadopor;
	@Column(length = 20)
	private String correlato;
	@Column(length = 20)
	private String controlado;
	@Column(length = 20)
	private String medicamento;
	@Column(length = 20)
	private String altavigilancia;
	@Column(length = 20)
	private String codbarra;
	private boolean permuta;
	@Column(length = 100)
	private String descpermuta;
	private int idzamed;
	private String unidade;

	public void iniciarvariavel() {
		material = "";
		fornecedor = "";
		notafiscal = "";
		tipo = "";
		quantidade = 0;
		String hoje = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new java.util.Date());
		fabricacao = hoje;
		validade = hoje;
		entrada = hoje;
		lote = "0";
		idzamed = 0;
		cadastradopor = "";
		atualizadopor = "";
		valor = 0;
		correlato = "";
		controlado = "";
		medicamento = "";
		altavigilancia = "";
		codbarra = "";
		descpermuta = "";
		permuta = false;
		unidade = "";

	}

}
