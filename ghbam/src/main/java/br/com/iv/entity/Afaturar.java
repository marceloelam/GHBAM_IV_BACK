package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

@Data
@Entity
@Table(name = "afaturar")
public class Afaturar  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idafaturar;
	@Column(length = 10)
	private String procedimento;
	@Column(length = 200)
	private String descricao;
	@Column(length = 20)
	private String local;
	@Column(length = 10)
	private String matriculaservidor;
	@Column(length = 55)
	private String nomeservidor;
	@Column(columnDefinition = "integer default 0")
	private int bam;
	@Column(columnDefinition = "integer default 0")
	private int idade;
	@Column(columnDefinition = "integer default 0")
	private int idtratamento;
	@Column(columnDefinition = "integer default 0")
	private int idregistro;
	@Column(columnDefinition = "integer default 0")
	private int idpaciente;
	private String data;
	@Column(length = 55)
	private String cbo;
	@Column(length = 700)
	private String obs;
	@Column(length = 19)
	private String localsolicitante;
	@Column(length = 55)
	private String controleid;
	private boolean realizado;
	@Column(columnDefinition = "integer default 0")
	private int laudoid;
	private double pa_sa; // Ambulatorial valor
	private double pa_sp; // profissional valor
	private double pa_sh; // Hospitalar valor
	private String pa_tpreg; // 01 - consolidado, 02 - individualizado e outros
	@Column(length = 1)
	private String pa_eletiva;

	// para BPA individualizado
	@Column(length = 16)
	private String cnsdoprofissional;
	@Column(length = 16)
	private String cnsdopciente;
	@Column(length = 1)
	private String sexo;
	@Column(length = 7)
	private String ibgecod; // código ibge do município da residência
	@Column(length = 12)
	private String dtatendimento;
	@Column(length = 4)
	private String cid10;
	@Column(columnDefinition = "integer default 0")
	private int carateratend;
	@Column(columnDefinition = "integer default 0")
	private int autorizacao; // número de autorização do estabelecimento
	@Column(length = 4)
	private String origeminfo; // bpa, ini, sie, sib, min, pac, scl. ext
	@Column(length = 30)
	private String nomepaciente;
	@Column(length = 12)
	private String dtnascimento;
	@Column(columnDefinition = "integer default 0")
	private int raca_cor;
	@Column(columnDefinition = "integer default 0")
	private int etnia_raca;
	@Column(columnDefinition = "integer default 0")
	private int nacionalidade;
	@Column(columnDefinition = "integer default 0")
	private int codservico;
	@Column(columnDefinition = "integer default 0")
	private int codclassificacao;
	@Column(columnDefinition = "integer default 0")
	private int cod_seq_equipe;
	@Column(columnDefinition = "integer default 0")
	private int cod_area_equipe;
	@Column(columnDefinition = "integer default 0")
	private int cod_cnpj;

	@Column(columnDefinition = "integer default 0")
	private int cep;
	@Column(columnDefinition = "integer default 0")
	private int cod_logradouro;
	@Column(length = 30)
	private String endereco;
	@Column(length = 10)
	private String complemento;
	@Column(length = 5)
	private String numerocasa;
	@Column(length = 30)
	private String bairro;
	private long telefone;
	@Column(length = 40)
	private String email;
	@Column(columnDefinition = "integer default 0")
	private int identnacequipe;
	@Column(length = 25)
	private String unidade;

	// Colunas do modulo Central Municipal de Faturamento SUS (Fase 1 - Fundacao).
	// Afaturar ja e, na pratica, a producao pre-faturamento (BPA-I/BPA-C via
	// pa_tpreg/pa_sa/pa_sp/pa_sh); estas colunas so adicionam o workflow de
	// competencia/status/critica/origem/auditoria em cima do que ja existia,
	// em vez de duplicar a tabela.
	private Integer idcompetencia;
	@Column(length = 30)
	private String status;
	@Column(name = "instrumento_registro", length = 30)
	private String instrumentoRegistro;
	@Column(name = "origem_modulo", length = 30)
	private String origemModulo;
	@Column(name = "origem_id")
	private Integer origemId;
	// Boolean (wrapper), nao boolean primitivo, de proposito: o mesmo padrao ja
	// usado em Servidor2 para colunas adicionadas sobre tabela ja populada -
	// evita que o Hibernate (dev, database.generation=update) gere "ADD COLUMN
	// ... NOT NULL" sem default e quebre a subida em cima de linhas existentes.
	@Column(name = "possui_critica", columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean possuiCritica;
	@Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturado;
	@Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean exportado;
	// Em qual remessa esta linha saiu. E o que permite cancelar uma remessa e devolver
	// exatamente a producao dela para o proximo arquivo, sem mexer no que saiu nas
	// outras. Nulo em linha exportada antes da V119 (o cancelamento trata esse caso a
	// parte) e em tudo que ainda nao foi exportado.
	@Column(name = "numero_remessa", length = 60)
	private String numeroRemessa;
	// Marca que instrumentoRegistro foi definido manualmente pelo usuario (tela de
	// producao) em vez de calculado por classificarInstrumento - acontece quando o
	// SIGTAP local (pa_tpreg) nao define BPA-I/BPA-C para o procedimento. Enquanto
	// true, reprocessarInterno respeita o valor gravado em vez de recalcular.
	@Column(name = "classificacao_manual", columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean classificacaoManual;
	private LocalDateTime datacriacao;
	private LocalDateTime dataalteracao;
	@Column(length = 40)
	private String usuariocriador;
	@Column(length = 40)
	private String usuarioalterador;

	// Nao persistidos: dado do paciente (Pacientes) so para as telas de producao
	// mostrarem "dados para busca" sem precisar de outra consulta.
	@Transient
	private String cpfPaciente;
	@Transient
	private String nascimentoPaciente;
	@Transient
	private String maePaciente;
	@Transient
	private String municipioPaciente;

	// Nao persistidos: dado do cadastro do servidor (Servidor, casado por
	// matriculaservidor). A producao guarda so matricula e nome; a ocupacao vive no
	// cadastro. As telas de faturamento usam isso para dizer qual e a profissao do
	// profissional da linha (medico, enfermeiro, dentista...) quando a producao sai sem
	// CBO - sem isso o operador nao sabe qual CBO deveria estar ali.
	@Transient
	private String cargoServidor;
	@Transient
	private String cboServidor;
	@Transient
	private String crmServidor;
	@Transient
	private String coremServidor;
	@Transient
	private String especialidadeServidor;

	// Nao persistido: motivo calculado pelo motor de faturamento SUS para a tela
	// explicar status como PRODUCAO_COM_CRITICA sem criar coluna nova.
	@Transient
	private String motivoStatus;

}
