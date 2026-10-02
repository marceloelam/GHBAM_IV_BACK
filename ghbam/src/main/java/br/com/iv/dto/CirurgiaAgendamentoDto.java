package br.com.iv.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class CirurgiaAgendamentoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idpaciente;
	private String nomepaciente;
	private String cnspaciente;
	private Integer idbam;
	private Integer idinternacao;
	private String unidade;
	private String cnes;
	private Integer idsala;
	private String dataPrevista;
	private Integer duracaoPrevistaMin;
	private String carater;
	private String porte;
	private String especialidade;
	private Integer idcirurgiao;
	private String nomecirurgiao;
	private String tipoAnestesia;
	private String lateralidade;
	private String cid;
	private String diagnostico;
	private Boolean necessitaUti;
	private Boolean necessitaHemocomponente;
	private String reservaSangue;
	private String observacao;
	private Integer idespera;
	private List<CirurgiaProcedimentoDto> procedimentos;
	private List<CirurgiaEquipeDto> equipe;
}
