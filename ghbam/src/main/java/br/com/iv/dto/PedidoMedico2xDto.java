package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class PedidoMedico2xDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idpedidomedico;
	private String pedido;
	private String local;
	private String real;
	private String medico;
	private String crm;
	private String setor;
	private String cod;
	private int posicao;
	private String entrada;
	private String obs;
	private String obs2;
	private String obs3;
	private String arquivo;
	private boolean efetuado;
	private boolean coletar;
	private boolean emergencia;
	private boolean internado;
	private boolean confirmado;
	private boolean medicoviu;
	private boolean eas;
	private String dt;
	private String hr;
	private String unidade;
	private String liberoucoleta;
	private String liberouexame;
	private String restaurou;
	private String idunico;

	private int idPaciente;
	private String nome;
	private Integer idade;
	private String pacienteespecial;
	private String nascimento;

	// ✅ novos (dados familiares)
	private String pai;
	private String mae;

	private int idBam;
	private Integer cor;

	// ✅ novos (joins)
	private String nomeSetor;
	private String nomeLocal;
}