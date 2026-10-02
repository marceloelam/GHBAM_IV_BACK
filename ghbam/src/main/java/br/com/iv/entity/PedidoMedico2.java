package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "pedidomedico2")
public class PedidoMedico2 extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = -345977257898752736L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idpedidomedico;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedido;
	@Column(length = 20) // nullable=false - Não pode ser nulo
	private String local;
	@Column(length = 20) // nullable=false - Não pode ser nulo
	private String real;
	@Column(length = 65) // nullable=false - Não pode ser nulo
	private String medico;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String crm;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String setor;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String cod;
	private int bam;
	private int posicao;
	@Column(length = 5)
	private String entrada;
	@Column(length = 1000)
	private String obs;
	@Column(length = 1000)
	private String obs2;
	@Column(length = 1000)
	private String obs3;
	@Column(length = 800)
	private String arquivo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean efetuado;
	@Column(nullable = true, columnDefinition = "BOOLEAN DEFAULT TRUE")
	private boolean coletar;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean emergencia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean internado;
	@Column(nullable = true, columnDefinition = "BOOLEAN DEFAULT TRUE")
	private boolean confirmado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean medicoviu;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean eas;
	@Column(length = 12)
	private String dt;
	@Column(length = 5)
	private String hr;
	@Column(length = 25)
	private String unidade;
	@Column(length = 65)
	private String liberoucoleta;
	@Column(length = 65)
	private String liberouexame;
	@Column(length = 65)
	private String restaurou;
	@Column(length = 65)
	private String idunico;

	public PedidoMedico2() {
		super();
		bam = 0;
		local = "";
		real = "";
		entrada = "";
		pedido = "";
		cod = "";
		setor = "";
		unidade = "";
		posicao = 0;
		efetuado = false;
		emergencia = false;
		confirmado = false;
		coletar = true;
		internado = false;
		medicoviu = false;
		medico = "";
		crm = "";
		arquivo = "";
		obs = "";
		liberoucoleta = "";
		liberouexame = "";
		restaurou = "";
		obs2 = "";
		idunico = "";
		obs3 = "";
		eas = false;

	}

}
