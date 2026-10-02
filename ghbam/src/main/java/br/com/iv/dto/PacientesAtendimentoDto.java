package br.com.iv.dto;

import java.io.Serializable;
import lombok.Data;
@Data
public class PacientesAtendimentoDto implements Serializable {
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
	private Integer cor;
	private int prioridade;
	private String hora;
	private boolean eletivo;
	private int idRegistro;
	private String atendidopor;
	private String setor;
	private int idTratamento;
	private Integer tratado; 

	// Explicit setters (compatibility in case Lombok annotation processing is not enabled)
	public void setIdPaciente(int idPaciente) {
		this.idPaciente = idPaciente;
	}

	public void setPacienteespecial(String pacienteespecial) {
		this.pacienteespecial = pacienteespecial;
	}

	public void setIdAvaliacao(int idAvaliacao) {
		this.idAvaliacao = idAvaliacao;
	}

	public void setIdBam(int idBam) {
		this.idBam = idBam;
	}

	public void setIdRegistro(int idRegistro) {
		this.idRegistro = idRegistro;
	}

	public void setPrioridade(int prioridade) {
		this.prioridade = prioridade;
	}

	public void setEletivo(boolean eletivo) {
		this.eletivo = eletivo;
	}

	public void setTratado(Integer tratado) {
		this.tratado = tratado;
	}

	}
