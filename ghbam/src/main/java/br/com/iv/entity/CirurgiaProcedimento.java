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
@Table(name = "cirurgia_procedimento")
public class CirurgiaProcedimento extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idprocedimento;

	@Column(nullable = false)
	private int idcirurgia;

	@Column(length = 15)
	private String codigo;

	@Column(length = 200, nullable = false)
	private String descricao;

	@Column(length = 10, nullable = false)
	private String tabela;

	@Column(nullable = false)
	private boolean principal;

	@Column(nullable = false)
	private int quantidade;

	@Column(length = 20)
	private String lateralidade;

	@Column(nullable = false)
	private boolean realizado;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	public CirurgiaProcedimento() {
		super();
		this.criadoEm = LocalDateTime.now();
		this.tabela = "SIGTAP";
		this.quantidade = 1;
	}
}
