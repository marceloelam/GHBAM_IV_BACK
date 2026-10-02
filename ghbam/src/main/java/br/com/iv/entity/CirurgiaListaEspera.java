package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "cirurgia_lista_espera")
public class CirurgiaListaEspera extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idespera;

	@Column(nullable = false)
	private int idpaciente;

	@Column(length = 80)
	private String nomepaciente;

	@Column(length = 19)
	private String cnspaciente;

	@Column(length = 25, nullable = false)
	private String unidade;

	@Column(length = 50)
	private String especialidade;

	@Column(name = "procedimento_codigo", length = 15)
	private String procedimentoCodigo;

	@Column(name = "procedimento_descricao", length = 200, nullable = false)
	private String procedimentoDescricao;

	@Column(length = 20, nullable = false)
	private String prioridade;

	@Column(name = "justificativa_prioridade", length = 300)
	private String justificativaPrioridade;

	@Column(name = "data_inclusao", nullable = false)
	private LocalDateTime dataInclusao;

	@Column(name = "data_prevista")
	private LocalDateTime dataPrevista;

	@Column
	private Integer idcirurgia;

	@Column(length = 20, nullable = false)
	private String status;

	@Column(length = 300)
	private String observacao;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em")
	private LocalDateTime atualizadoEm;

	@Column(name = "usuario_criador", length = 40)
	private String usuarioCriador;

	// Dados do cadastro do paciente, anexados na leitura da fila para o
	// agendamento nao precisar de uma segunda consulta: prontuario e o que a
	// equipe le, CPF e nascimento sao o que a consulta de CNS no CNES exige.
	@Transient
	private String prontuario;

	@Transient
	private String cpf;

	@Transient
	private String nascimento;

	public CirurgiaListaEspera() {
		super();
		this.criadoEm = LocalDateTime.now();
		this.dataInclusao = LocalDateTime.now();
		this.status = "AGUARDANDO";
		this.prioridade = "ELETIVA";
	}
}
