package br.com.iv.entity;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "resumo_balanco_hidrico")
public class ResumoBalancoHidrico extends PanacheEntityBase implements Serializable {
	
	private static final long serialVersionUID = 1L;

	@Id
	private String uuid; // UUID para controle frontend

	private String bam; // Referência principal

	private String dataBalanco; // Data do balanço (24h)

	@Column(name = "total_entradas_ml", nullable = false)
	private Integer totalEntradasMl;

	@Column(name = "total_saidas_ml", nullable = false)
	private Integer totalSaidasMl;

	@Column(name = "saldo_hidrico_ml", nullable = false)
	private Integer saldoHidricoMl; // Entradas - Saídas

	@Column(name = "diurese_total_ml")
	private Integer diureseTotalMl; // Apenas saídas tipo URINA

	@Column(name = "diurese_ml_kg_h", precision = 5)
	private Double diureseMlKgH; // Diurese / (peso * 24h)

	@Enumerated(EnumType.STRING)
	@Column(name = "classificacao_balanco", nullable = false)
	private ClassificacaoBalanco classificacaoBalanco;

	@Column(name = "alertas_clinicos", length = 1000)
	private String alertasClinicos; // JSON ou texto separado por vírgula

	@Column(name = "observacoes", length = 1000)
	private String observacoes;

	private String dataCriacao;

	private String dataAtualizacao;
	//private String servidor;

	
	
	protected String converteData() {
		Date dataAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("yyyy/MM/dd");
		String dataFormatada = formatador.format(dataAtual);

		return dataFormatada;
	}

	protected String converteTime() {
		Date horaAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("HH:mm");
		String timeFormatada = formatador.format(horaAtual);

		return timeFormatada;
	}
	
	// Lifecycle callbacks
	@PrePersist
	protected void onCreate() {
		dataCriacao = converteData();
		dataAtualizacao = converteData();
		calcularClassificacao();
	}

	@PreUpdate
	protected void onUpdate() {
		dataAtualizacao = converteData();
		calcularClassificacao();
	}

	// Método para calcular classificação do balanço
	private void calcularClassificacao() {
		if (saldoHidricoMl == null)
			return;

		if (saldoHidricoMl > 0) {
			this.classificacaoBalanco = ClassificacaoBalanco.POSITIVO;
		} else if (saldoHidricoMl < 0) {
			this.classificacaoBalanco = ClassificacaoBalanco.NEGATIVO;
		} else {
			this.classificacaoBalanco = ClassificacaoBalanco.NEUTRO;
		}
	}

	// Enum para classificação do balanço
	public enum ClassificacaoBalanco {
		POSITIVO("Balanço Positivo"), NEGATIVO("Balanço Negativo"), NEUTRO("Balanço Neutro");

		private final String descricao;

		ClassificacaoBalanco(String descricao) {
			this.descricao = descricao;
		}

		public String getDescricao() {
			return descricao;
		}
	}

	// Getters e Setters
	// ... (implementar todos os getters e setters)

	// Setter personalizado para saldo hídrico (recalcula classificação)
	public void setSaldoHidricoMl(Integer saldoHidricoMl) {
		this.saldoHidricoMl = saldoHidricoMl;
		calcularClassificacao();
	}
}