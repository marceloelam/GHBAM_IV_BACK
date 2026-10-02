package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.Data;

@Data
public class RegistroDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 10)
	private String responsavel;
	@Column(length = 20)
	private String setor;
	@Column(length = 20)
	private String especialidade;
	@Column(length = 20)
	private String convenio;
	@Column(length = 55)
	private String tipochegada;
	@Column(length = 55)
	private String tipoocorrencia;
	@Column(length = 55)
	private String queixaprincipal;
	@Column(length = 35)
	private String causalesao;
	@Column(length = 350)
	private String obsentrada;
	@Column(length = 25)
	private String controle;
	@Column(length = 50)
	private String atendidopor;
	@Column(length = 15)
	private String matriculaatendidopor;
	@Temporal(TemporalType.DATE)
	private Date dtsintomas;
	private int resultadoexamec19;
	private boolean eletivo;
	private boolean eletivointernado;
	private boolean resultadoexame;
	private boolean resultadorx;
	private boolean resultadotomo;
	private boolean resultadomed;
	private boolean resultadolabo;
	private boolean resultadoenfe;
	private boolean temlabo;
	private boolean temrx;
	private boolean temtomo;
	private boolean temmed;
	private boolean temenfe;
}
