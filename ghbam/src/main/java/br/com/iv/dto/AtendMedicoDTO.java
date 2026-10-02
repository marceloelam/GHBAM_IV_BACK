package br.com.iv.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class AtendMedicoDTO {
	private int idbam;
	private Integer cor;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
	private Date data;
	private String hora;
	private boolean ematendimento;
	private boolean transferido;
	private int idpaciente;
	private String nome;
	private Integer idade;
	private String pacienteespecial;
	private String mae;
	private String pai;
	private String nascimento;
	private int idavaliacao;
	private int avaliado;
	private int idRegistro;
	private boolean resultadorx;
	private boolean resultadotomo;
	private boolean resultadomed;
	private boolean resultadoexame;
	private boolean resultadolabo;
	private boolean resultadoenfe;
	private boolean temlabo;
	private boolean temrx;
	private boolean temtomo;
	private boolean temmed;
	private boolean temenfe;
	
	private String atendidopor;
	private String setor;
	private int idTratamento;
	private Integer tratado;
	
	private int idescalapaciente;
	private String medico;
	
}
