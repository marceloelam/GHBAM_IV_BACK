package br.com.iv.dto;

import java.io.Serializable;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class DriveDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 120)
	private String titulo;
	@Column(length = 500)
	private String descricao;
	@Column(length = 500)
	private String url;
	@Column(length = 60)
	private String menu;
	@Column(length = 30)
	private String unidade;
}
