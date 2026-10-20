package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class PacientesNirDto implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private int idpaciente;
	private String nome;
	private int idade;
	private String prontuario;
	private String pacienteespecial;
	private int bam;
	private Date data;
	private int internacao;
	private Date datainternacao;
	private Date datasaida;
	private String motivodaalta;
	private String medicoqueinternou;
	private String medicodeualta;
	private int novointernado;
	private Date diainternacao;
	private Boolean alta;
	private int setorcenso;
	private String setor;
	private int localcenso;
	private String local;
	private int tratamento;
	private String tipo_alta;
	private int laudoautorizacao;
	private int bamlaudo;
	private int resumodealta;
	private Date dataalta;
	private String diagfinal;
	private Date dataacao;
	private String diagnostico;
	// NIR "novo": outros BAMs com pedido de internação e BAM posterior ao pedido.
	private String outrosbamspedido;
	private int bamposterior;
	private int baminternado;
	
	
}

