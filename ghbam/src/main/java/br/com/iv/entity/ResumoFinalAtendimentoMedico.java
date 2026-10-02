package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
@Data
@Entity(name = "FinalMedico")
public class ResumoFinalAtendimentoMedico   extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idfinalMedico;
	@Column(length = 13)
	private String dataAtendimento;
	@Column(length = 5)
	private String horasaida;
	private int totalatendimentos;
	@Column(length = 55)
	private String medico;
	@Column(length = 25)
	private String setor;
	@Column(length = 25)
	private String unidade;

}
