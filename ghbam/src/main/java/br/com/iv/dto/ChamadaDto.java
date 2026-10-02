package br.com.iv.dto;

import java.io.Serializable;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class ChamadaDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 70, nullable = false)
	private String nome;
	@Column(length = 50, nullable = false)
	private String local;
	private boolean chamou;
	private boolean chamou2;
	private String data;
	private String hora;
	@Column(length = 20)
	private String setor;
	@Column(length = 35)
	private String unidade;
	@Column(length = 20)
	private String pacienteespecial;
	private boolean p1;
	private boolean p2;
	private boolean p3;
	private boolean p4;
	private boolean p5;
	private boolean p6;
	private boolean p7;
	private boolean p8;
	private boolean p9;
	private boolean p10;
	
}
