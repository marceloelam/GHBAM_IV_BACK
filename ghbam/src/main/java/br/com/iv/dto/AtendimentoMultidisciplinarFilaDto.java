package br.com.iv.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class AtendimentoMultidisciplinarFilaDto implements Serializable {
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
	private int cor;
	private int idRegistro;
	private String setor;
	private int idTratamento;
	private Integer tratado;
	private String categoriaProfissional;
	private Integer idAtendimento;
	private String statusAtendimento;
	private boolean agendado;
	private String modalidade;
	private String medicoAgendado;
}
