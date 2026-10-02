package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class CirurgiaSuspensaoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private String categoria;
	private String motivo;
	private Boolean devolverParaListaEspera;
}
