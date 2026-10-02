package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import lombok.Data;

import com.fasterxml.jackson.annotation.JsonFormat;



@Data
public class AtendimentoMedicoDto implements Serializable {
	private static final long serialVersionUID = 1L;
	private int idatendimento;
	@Column(columnDefinition = "TEXT")
	private String receita;
	@Column(columnDefinition = "TEXT")
	private String relato;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
	private Date data;
	@Column(length = 5)
	private String hora;
	@Column(length = 45)
	private String nomedomedico;
	private int bam;
	private int idpaciente;
}
