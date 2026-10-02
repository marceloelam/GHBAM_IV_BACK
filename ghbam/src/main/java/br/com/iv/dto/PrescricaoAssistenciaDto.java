package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class PrescricaoAssistenciaDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(columnDefinition = "TEXT")
	private String observacaoassist;
	private int bam;
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 19)
	private String localsolicitante;
	@Column(length = 55)
	private String tecnico;


}
