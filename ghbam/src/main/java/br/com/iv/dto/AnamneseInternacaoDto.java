package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;


import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class AnamneseInternacaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 7200)
	private String frente;
	@Temporal(TemporalType.DATE)
	private Date data;
	@Column(length = 7200)
	private String verso;
	@Column(length = 15)
	private String prontuario;
	@Column(length = 55)
	private String medico;
	@Column(length = 15)
	private String crm;

	private int nbam;
	
}
