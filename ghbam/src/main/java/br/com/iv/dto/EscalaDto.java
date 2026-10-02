package br.com.iv.dto;

import java.io.Serializable;
import java.util.Date;

import br.com.iv.entity.Servidor;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

@Data
public class EscalaDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idescala;
	private String titulo;
	@Temporal(TemporalType.DATE)
	private Date inicioagenda;
	@Temporal(TemporalType.DATE)
	private Date fimagenda;
	private String horaescala;
	@Column(length = 30)
	private String setor;
	private String horasaida;
	@ManyToOne
	@JoinColumn(nullable = false)
	private Servidor servidor;
	@ManyToOne
	@JoinColumn(nullable = false)
	private Servidor cadastrou;
	@Column(length = 30)
	private String unidade;
	@Column(length = 600)
	private String obs;
	@Column(length = 600)
	private String obs2;
	private boolean statusevento;

}
