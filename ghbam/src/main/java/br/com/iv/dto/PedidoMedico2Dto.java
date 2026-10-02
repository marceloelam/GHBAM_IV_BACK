package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class PedidoMedico2Dto implements Serializable {
	private static final long serialVersionUID = 1L;

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
	private boolean efetuado;
	private boolean coletar;
	private boolean emergencia;
	private boolean internado;
	private boolean confirmado;
	private boolean medicoviu;
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
}
