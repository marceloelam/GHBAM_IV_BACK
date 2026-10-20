package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class UnidadeVideoConfigDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 20)
	private String tipoTransicao;
	private double duracaoTransicao;
	private boolean mudo;
	private int volume;
	@Column(length = 10)
	private String modo;
	@Column(columnDefinition = "TEXT")
	private String urlM3u;
	@Column(length = 120)
	private String tituloM3u;

	// null = não alterar (telas antigas não enviam); "" = voltar para a voz padrão.
	private String vozPainel;

	// Mesma regra: null = não alterar; "" = voltar para a frase padrão.
	private String fraseChamada;
}
