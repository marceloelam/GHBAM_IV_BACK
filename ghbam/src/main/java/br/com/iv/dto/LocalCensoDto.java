package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import lombok.Data;
import br.com.iv.entity.SetorCenso;

@Data
public class LocalCensoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idsetorlocalcenso;
	private String codigo;
	private String local;
	private boolean disponivel;
	private boolean excluido;
	private String unidade;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false)
	private SetorCenso setorCenso;

	public LocalCensoDto(int idsetorlocalcenso,  String local, String unidade, boolean disponivel, boolean excluido	) {
		super();
		this.idsetorlocalcenso = idsetorlocalcenso;
		this.local = local;
		this.disponivel = disponivel;
		this.excluido = excluido;
		this.unidade = unidade;
	}

	

	
	
}
