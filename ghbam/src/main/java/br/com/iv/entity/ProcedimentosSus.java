package br.com.iv.entity;

import java.io.Serializable;

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
@Table(name = "s_proced")
public class ProcedimentosSus  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idprocedimentossus;
	@Column(length = 6)
	private String pa_cmp;
	@Column(length = 9)
	private String pa_id;
	@Column(length = 1)
	private String pa_dv;
	@Column(length = 60)
	private String pa_dc;
	private double pa_sa; // Ambulatorial valor
	private double pa_sp;
	private double pa_sh; // Hospitalar valor
	@Column(length = 1)
	private String pa_eletiva;
	private String pa_tpreg;
	// Campos do tb_procedimento.txt do DATASUS usados pelo motor de validacao (V139).
	// Nulos em competencia importada antes da V139 - a validacao ignora quando nulo.
	@Column(length = 1)
	private String tp_sexo;
	private Integer vl_idade_minima;
	private Integer vl_idade_maxima;
	private Integer qt_maxima_execucao;
	@Column(length = 1)
	private String tp_complexidade;
	@Column(length = 2)
	private String co_financiamento;
	

}
