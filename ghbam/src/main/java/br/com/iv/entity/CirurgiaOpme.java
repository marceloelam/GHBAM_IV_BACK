package br.com.iv.entity;

import java.io.Serializable;
import java.math.BigDecimal;
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
@Table(name = "cirurgia_opme")
public class CirurgiaOpme extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idopme;

	@Column(nullable = false)
	private int idcirurgia;

	@Column(length = 20)
	private String codigo;

	@Column(length = 200, nullable = false)
	private String descricao;

	@Column(length = 80)
	private String fabricante;

	@Column(length = 80)
	private String fornecedor;

	@Column(length = 40)
	private String lote;

	@Column(name = "registro_anvisa", length = 30)
	private String registroAnvisa;

	@Column(length = 10)
	private String validade;

	@Column(nullable = false)
	private int quantidade;

	@Column(name = "valor_unitario", precision = 12, scale = 2)
	private BigDecimal valorUnitario;

	@Column(nullable = false)
	private boolean utilizado;

	@Column(nullable = false)
	private boolean devolvido;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaOpme() {
		super();
		this.criadoEm = LocalDateTime.now();
		this.quantidade = 1;
	}
}
