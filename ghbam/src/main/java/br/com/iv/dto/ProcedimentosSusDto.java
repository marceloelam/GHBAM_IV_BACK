package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class ProcedimentosSusDto implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@Column(length = 6)
	private String pa_cmp;
	@Column(length = 9)
	private String pa_id;
	@Column(length = 1)
	private String pa_dv;
	@Column(length = 60)
	private String pa_dc;
	private double pa_sa; // Ambulatorial valor
	private double pa_sp;
	private double pa_sh; // Hospitalar valor
	@Column(length = 1)
	private String pa_eletiva;
	private String pa_tpreg;
}
