package br.com.iv.dto;

import java.util.ArrayList;
import java.util.List;

public class PedidoDTO {
	private String origem;
	private String unidade;
	private String unidadeSigla;
	private String comentario;
	private List<PedidoItemDTO> itens = new ArrayList<>();

	public String getUnidade() {
		return unidade;
	}

	public void setUnidade(String unidade) {
		this.unidade = unidade;
	}

	public String getUnidadeSigla() {
		return unidadeSigla;
	}

	public void setUnidadeSigla(String unidadeSigla) {
		this.unidadeSigla = unidadeSigla;
	}

	public String getComentario() {
		return comentario;
	}

	public void setComentario(String comentario) {
		this.comentario = comentario;
	}

	public List<PedidoItemDTO> getItens() {
		return itens;
	}

	public void setItens(List<PedidoItemDTO> itens) {
		this.itens = itens;
	}

	public String getOrigem() {
		return origem;
	}

	public void setOrigem(String origem) {
		this.origem = origem;
	}

}
