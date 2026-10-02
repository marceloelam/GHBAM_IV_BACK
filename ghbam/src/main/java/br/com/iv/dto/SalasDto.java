package br.com.iv.dto;

import java.io.Serializable;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class SalasDto implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Column(length = 40)
	private String sala;
	@Column(length = 40)
	private String unidade;
	@Column(length = 1)
	private String local;
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
