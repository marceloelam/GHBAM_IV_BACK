package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;


@Data
public class AfaturarDto implements Serializable {
	private static final long serialVersionUID = 1L;


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
	// Chave de origem do lancamento: modulo assistencial + id do registro que o gerou
	// (pedido de exame, triagem, atendimento...). Quando vem preenchida, o mesmo
	// lancamento enviado duas vezes nao duplica producao - ver
	// AfaturarService.salvarFatura. Opcional: quem nao envia continua criando linha
	// nova a cada chamada, como sempre foi.
	@Column(length = 30)
	private String origemModulo;
	private Integer origemId;

}
