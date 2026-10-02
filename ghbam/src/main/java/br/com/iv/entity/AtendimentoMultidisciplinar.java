package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "AtendimentoMultidisciplinar")
public class AtendimentoMultidisciplinar extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

	private int idpaciente;
	private Integer idbam;
	@Column(length = 40)
	private String prontuario;
	@Column(length = 20)
	private String unidade;
	@Column(length = 60)
	private String setor;
	@Column(length = 30)
	private String leito;

	@Column(length = 40)
	private String categoriaProfissional;
	@Column(length = 120)
	private String profissional;
	@Column(length = 40)
	private String matriculaProfissional;
	@Column(length = 15)
	private String conselho;
	@Column(length = 30)
	private String numeroConselho;

	@Temporal(TemporalType.DATE)
	private Date dataAtendimento;
	@Column(length = 5)
	private String horaInicial;
	@Column(length = 5)
	private String horaFinal;
	@Column(length = 4000)
	private String motivo;
	@Column(length = 40)
	private String origem;
	@Column(length = 120)
	private String solicitante;
	@Column(length = 15)
	private String prioridade;
	@Column(length = 30)
	private String tipoAtendimento;
	private Boolean acompanhantePresente;
	@Column(length = 4000)
	private String observacoesIniciais;

	@Column(length = 4000)
	private String queixaPrincipal;
	@Column(length = 4000)
	private String historiaAtual;
	@Column(length = 4000)
	private String condicaoClinicaObservada;
	@Column(length = 30)
	private String nivelConsciencia;
	@Column(length = 30)
	private String orientacao;
	@Column(length = 2000)
	private String comunicacao;
	@Column(length = 2000)
	private String mobilidade;
	@Column(length = 2000)
	private String autonomia;
	@Column(length = 30)
	private String dor;
	@Column(length = 2000)
	private String alimentacaoAvaliacao;
	@Column(length = 2000)
	private String degluticao;
	@Column(length = 2000)
	private String respiracao;
	@Column(length = 2000)
	private String eliminacoes;
	@Column(length = 2000)
	private String condicoesEmocionais;
	@Column(length = 2000)
	private String condicoesFamiliares;
	@Column(length = 2000)
	private String redeApoio;
	@Column(length = 2000)
	private String vulnerabilidadeSocial;
	@Column(length = 2000)
	private String adesaoTratamento;
	@Column(length = 2000)
	private String riscosIdentificados;
	@Column(length = 2000)
	private String necessidadesIdentificadas;
	@Column(length = 4000)
	private String observacoesGerais;

	// JSON em texto com campos especificos por categoria profissional
	// (evita 100+ colunas fixas para 7 categorias diferentes).
	@Column(length = 8000)
	private String dadosEspecificos;

	@Column(length = 2000)
	private String objetivos;
	@Column(length = 4000)
	private String condutas;
	@Column(length = 4000)
	private String intervencoes;
	@Column(length = 2000)
	private String orientacoes;
	@Column(length = 2000)
	private String encaminhamentosTexto;
	@Column(length = 2000)
	private String solicitacoes;
	@Column(length = 2000)
	private String metas;
	@Column(length = 60)
	private String prazoEstimado;
	private Boolean necessitaRetorno;
	@Temporal(TemporalType.DATE)
	private Date dataPrevistaReavaliacao;

	@Column(length = 4000)
	private String evolucao;
	@Column(length = 2000)
	private String respostaIntervencoes;
	@Column(length = 2000)
	private String intercorrencias;
	@Column(length = 2000)
	private String alteracaoConduta;
	@Column(length = 2000)
	private String orientacoesFornecidas;
	@Column(length = 2000)
	private String contatoFamiliares;
	@Column(length = 2000)
	private String contatoOutrosProfissionais;
	@Column(length = 2000)
	private String resultadoAtendimento;

	@Column(length = 2000)
	private String condicaoTermino;
	private Boolean atendimentoConcluido;
	private Boolean necessitaAcompanhamento;
	private Boolean necessitaRetornoFinal;
	private Boolean encaminhamentoRealizado;
	private Boolean transferenciaSetor;
	private Boolean transferenciaUnidade;
	private Boolean altaMultidisciplinar;
	@Column(length = 2000)
	private String observacoesFinais;
	@Column(columnDefinition = "text")
	private String avaliacaoIa;
	@Temporal(TemporalType.TIMESTAMP)
	private Date dataEncerramento;

	@Column(length = 20)
	private String status = "AGUARDANDO";
	private boolean ativo = true;
	@Column(length = 2000)
	private String motivoCancelamento;

	@Column(length = 40)
	private String criadoPorMatricula;
	@Temporal(TemporalType.TIMESTAMP)
	private Date criadoEm;
	@Column(length = 40)
	private String atualizadoPorMatricula;
	@Temporal(TemporalType.TIMESTAMP)
	private Date atualizadoEm;
	private int versao = 0;

	public AtendimentoMultidisciplinar() {
		super();
	}

	@PrePersist
	protected void aoPersistir() {
		if (criadoEm == null) {
			criadoEm = new Date();
		}
		if (dataAtendimento == null) {
			dataAtendimento = new Date();
		}
		atualizadoEm = new Date();
	}

	@PreUpdate
	protected void aoAtualizar() {
		atualizadoEm = new Date();
		versao = versao + 1;
	}

}
