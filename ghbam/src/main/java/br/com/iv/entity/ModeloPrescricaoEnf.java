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
@Table(name = "modeloprescricaoenf")
public class ModeloPrescricaoEnf extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idmodelo;
	@Column(columnDefinition = "TEXT")
	private String modelo;
	@Column(length = 45)
	private String titulo;
	@Column(length = 85)
	private String unidade;
	@Column(length = 45)
	private String sigla;
	@Column(length = 115)
	private String criador;

}
