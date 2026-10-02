package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class ProcedimentosPasrvDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 6)
	private String pasrv_cmp;
	@Column(length = 9)
	private String pasrv_pa;
	@Column(length = 3)
	private String pasrv_srv;
	@Column(length = 3)
	private String pasrv_csf;
	@Column(length = 6)
	private String pasrvchksm;
}
