package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class InternacaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private String procedencia;
	private LocalDate datainternacao;
	@Column(length = 20)
	private String prontuario;
	@Column(length = 20)
	private String tpdocumento;
	@Column(length = 20)
	private String ndocumento;
	@Column(length = 20)
	private String bam;
	@Column(length = 20)
	private String cid;
	@Column(length = 40)
	private String local;
	@Column(length = 55)
	private String medicoqueinternou;
	private LocalDate datasaida;
	@Column(length = 55)
	private String motivodaalta;
	@Column(length = 55)
	private String medicodeualta;
	@Column(length = 410)
	private String obs;
	@Column(length = 55)
	private String atendente;
	@Column(length = 10)
	private String origem;
	@Column(length = 25)
	private String unidade;
	private int idDoPaciente;

}
