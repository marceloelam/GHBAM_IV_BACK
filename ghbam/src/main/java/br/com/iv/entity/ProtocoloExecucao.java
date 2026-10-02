package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "protocolo_execucao")
public class ProtocoloExecucao extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(name = "protocolo_id", nullable = false)
	private int protocoloId;

	@Column(length = 100, nullable = false)
	private String protocoloNome;

	@Column(name = "unidade", nullable = false)
	private String unidade;

	@Column(name = "servidor_criador", length = 100)
	private String servidorCriador;

	@Column(name = "servidor_executor", length = 100)
	private String servidorExecutor;

	@Column(name = "paciente_id")
	private Integer pacienteId;

	@Column(name = "paciente_nome", length = 200)
	private String pacienteNome;

	@Column(name = "paciente_mae", length = 200)
	private String pacienteMae;

	@Column(name = "paciente_idade")
	private Integer pacienteIdade;

	@Column(name = "data_execucao", nullable = false)
	private LocalDateTime dataExecucao;

	@Column(columnDefinition = "TEXT")
	private String resultadosJson; // Dados preenchidos pelo usuario

	@Column(columnDefinition = "TEXT")
	private String acoesExecutadasJson; // Ações executadas e seus resultados

	@Column(columnDefinition = "TEXT")
	private String alertasJson; // Alertas geradas

	@Column(columnDefinition = "TEXT")
	private String examesSolicitadosJson; // Exames solicitados

	@Column(columnDefinition = "TEXT")
	private String tarefasCriadasJson; // Tarefas criadas

	@Column(columnDefinition = "TEXT")
	private String imprimirJson; // Ações de impressão

	@Column(length = 20)
	private String status; // 'em_andamento', 'concluido', 'cancelado'

	@Column(length = 500)
	private String observacoes;

	public ProtocoloExecucao() {
		super();
		this.dataExecucao = LocalDateTime.now();
		this.status = "em_andamento";
	}
}
