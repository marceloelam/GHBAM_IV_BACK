package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class AltaadmnirDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int bam;
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 150)
	private String servidormatricula;

	
}
