package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class PacienteResultadoExameDto implements Serializable {
	private static final long serialVersionUID = 1L;
	private int idPaciente;
	private String nome;
	private Integer idade;
	private String pacienteespecial;
	private String nascimento;
	private int idBam;
	private int idpedidomedico;
	private Integer tratado; 
	private boolean efetuado;
	private boolean confirmado;
	private boolean coletar;
	
}
