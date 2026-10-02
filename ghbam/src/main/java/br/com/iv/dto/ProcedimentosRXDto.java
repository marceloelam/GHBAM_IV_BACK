package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class ProcedimentosRXDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 15)
	private String codprocedimento;
	@Column(length = 4)
	private String cid;
	@Column(length = 120)
	private String descricao;
	@Column(length = 30)
	private String modo;
	@Column(length = 15)
	private String modo2;
}
