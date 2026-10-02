package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class PedidoMedico2yDto implements Serializable {
	private static final long serialVersionUID = 1L;
	private String data;
	private String hora;
	private String local;
	private String obs;
	private String obs2;
	private int bam;
	
}
