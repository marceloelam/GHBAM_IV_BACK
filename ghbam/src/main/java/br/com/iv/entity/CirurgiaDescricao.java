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
@Table(name = "cirurgia_descricao")
public class CirurgiaDescricao extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer iddescricao;

	@Column(nullable = false, unique = true)
	private int idcirurgia;

	@Column(columnDefinition = "TEXT")
	private String achados;

	@Column(columnDefinition = "TEXT")
	private String descricao;

	@Column(columnDefinition = "TEXT")
	private String intercorrencias;

	@Column(name = "sangramento_ml")
	private Integer sangramentoMl;

	@Column(length = 200)
	private String hemocomponentes;

	@Column(name = "material_anatomia_patologica", nullable = false)
	private boolean materialAnatomiaPatologica;

	@Column(name = "material_anatomia_descricao", length = 200)
	private String materialAnatomiaDescricao;

	@Column(length = 200)
	private String drenos;

	@Column(name = "condicao_saida", length = 50)
	private String condicaoSaida;

	@Column(name = "destino_pos", length = 30)
	private String destinoPos;

	@Column(nullable = false)
	private boolean assinado;

	@Column(name = "assinado_em")
	private LocalDateTime assinadoEm;

	@Column(name = "assinado_por", length = 80)
	private String assinadoPor;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em")
	private LocalDateTime atualizadoEm;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaDescricao() {
		super();
		this.criadoEm = LocalDateTime.now();
	}
}
