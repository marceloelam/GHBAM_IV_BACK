package br.com.iv.dto;

import java.io.Serializable;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class TratamentoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(length = 1500)
	private String exames_solicitados;
	@Column(length = 650)
	private String diagnostico_provisorio;
	@Column(length = 1500)
	private String prescricao_inicial;
	@Column(length = 900)
	private String evolucao_prescricao;
	@Column(length = 105)
	private String destino;
	@Column(length = 105)
	private String encaminhamento;
	@Column(length = 10)
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 650)
	private String obs_saida;
	@Column(length = 1000)
	private String parecer_especialista;
	@Column(length = 650)
	private String obs_enfermagem;
	@Column(length = 20)
	private String tipo_alta;
	private int tratado;
	@Column(length = 40)
	private String controle;
	@Column(length = 100)
	private String medicoecrm;

}
