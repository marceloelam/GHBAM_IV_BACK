package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class PacienteDto implements Serializable {
	private static final long serialVersionUID = 1L;
	private int idPaciente;
	private String nome;
	private Integer idade;
	private String pacienteespecial;
	private String mae;
	private String pai;
	private String nascimento;
	private int idAvaliacao;
	private String dataav;
	private int avaliado;
	private int idBam;
	private int prioridade;
	private String hora;
	private boolean eletivo;
	private int idRegistro;
	private String setor;
	private int idTratamento;
	private Integer tratado; 
}
