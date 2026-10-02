package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class Cid1Dto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 4, nullable = false)
	private String cd_cod;
	@Column(length = 50, nullable = false)
	private String cd_descr;
	@Column(length = 1)
	private String opc;
	@Column(length = 1)
	private String cat;
	@Column(length = 1)
	private String subcat;
	@Column(length = 1)
	private String restrsexo;
	@Column(length = 3)
	private String campos_rad;
	@Column(length = 1)
	private String estadio;
	@Column(length = 1)
	private String repete_rad;
	@Column(length = 6)
	private String cmp_ini;
	@Column(length = 6)
	private String cmp_fim;
	@Column(length = 6)
	private String cid_chksm;

}
