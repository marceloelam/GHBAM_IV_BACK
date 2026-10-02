package br.com.iv.entity;

import java.io.Serializable;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

// Um exame de um laudo laboratorial (V189). codigoLoinc nulo = nao reconhecido: bloqueia o envio RNDS.
@Data
@Entity
@Table(name = "pedidoresultadolaboratorio_item")
public class PedidoResultadoLaboratorioItem extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long iditem;

	@Column(nullable = false)
	private Integer idpedidoresultado;

	@Column(name = "nome_exame", length = 120, nullable = false)
	private String nomeExame;

	@Column(name = "codigo_loinc", length = 30)
	private String codigoLoinc;

	@Column(length = 60, nullable = false)
	private String valor;

	@Column(name = "unidade_medida", length = 50)
	private String unidadeMedida;

	@Column(name = "referencia_valor", length = 120)
	private String referenciaValor;

	@Column(nullable = false)
	private Integer ordem;

	public static List<PedidoResultadoLaboratorioItem> doResultado(int idpedidoresultado) {
		return list("idpedidoresultado = ?1 order by ordem", idpedidoresultado);
	}
}
