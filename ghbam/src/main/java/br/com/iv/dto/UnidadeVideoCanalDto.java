package br.com.iv.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeVideoCanalDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;
	private String nome;
	private String descricao;
	private String logo;
	private String url;
	private int ordem;
}
