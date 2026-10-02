package br.com.iv.dto;

import lombok.Data;

@Data
public class AtendimentoMultidisciplinarDto {

	private int idpaciente;
	private Integer idbam;
	private String prontuario;
	private String unidade;
	private String setor;
	private String leito;

	private String categoriaProfissional;
	private String profissional;
	private String conselho;
	private String numeroConselho;

	private String horaInicial;
	private String horaFinal;
	private String motivo;
	private String origem;
	private String solicitante;
	private String prioridade;
	private String tipoAtendimento;
	private Boolean acompanhantePresente;
	private String observacoesIniciais;

	private String queixaPrincipal;
	private String historiaAtual;
	private String condicaoClinicaObservada;
	private String nivelConsciencia;
	private String orientacao;
	private String comunicacao;
	private String mobilidade;
	private String autonomia;
	private String dor;
	private String alimentacaoAvaliacao;
	private String degluticao;
	private String respiracao;
	private String eliminacoes;
	private String condicoesEmocionais;
	private String condicoesFamiliares;
	private String redeApoio;
	private String vulnerabilidadeSocial;
	private String adesaoTratamento;
	private String riscosIdentificados;
	private String necessidadesIdentificadas;
	private String observacoesGerais;

	private String dadosEspecificos;

	private String objetivos;
	private String condutas;
	private String intervencoes;
	private String orientacoes;
	private String encaminhamentosTexto;
	private String solicitacoes;
	private String metas;
	private String prazoEstimado;
	private Boolean necessitaRetorno;
	private String dataPrevistaReavaliacao;

	private String evolucao;
	private String respostaIntervencoes;
	private String intercorrencias;
	private String alteracaoConduta;
	private String orientacoesFornecidas;
	private String contatoFamiliares;
	private String contatoOutrosProfissionais;
	private String resultadoAtendimento;

	private String condicaoTermino;
	private Boolean atendimentoConcluido;
	private Boolean necessitaAcompanhamento;
	private Boolean necessitaRetornoFinal;
	private Boolean encaminhamentoRealizado;
	private Boolean transferenciaSetor;
	private Boolean transferenciaUnidade;
	private Boolean altaMultidisciplinar;
	private String observacoesFinais;
	private String avaliacaoIa;

	private int versao;

	// Transitorios — usados so na finalizacao, para repassar ao AssinaturaClinicaService.
	private String conteudoHash;
	private String conteudoResumo;

}
