package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class BalancoHibricoSaidaDto implements Serializable {
	private static final long serialVersionUID = 1L;

	  private String uuid;
	    private String hora;
	    private String tipo;          // ORAL, PARENTERAL, ENTERAL
	    private Integer volumeMl;
	    private String observacao;
	    private String bam;
	    private String profissional;
	    private String turno;
	   
}
