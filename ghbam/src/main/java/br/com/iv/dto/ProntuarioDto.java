package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;

import lombok.Data;

@Data
public class ProntuarioDto implements Serializable {
	private static final long serialVersionUID = 1L;

	//private int idprontuario;
	@Column(length = 18)
	private String prontuario;
	@Column(length = 40)
	private String controle;

}
