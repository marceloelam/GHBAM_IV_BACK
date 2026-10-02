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
@Table(name = "drives")
public class Drive extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int iddrive;

	@Column(length = 120, nullable = false)
	private String titulo;

	@Column(length = 500)
	private String descricao;

	@Column(length = 500, nullable = false)
	private String url;

	@Column(length = 60, nullable = false)
	private String menu;

	@Column(length = 30, nullable = false, columnDefinition = "varchar(30) not null default ''")
	private String unidade;

	public Drive() {
		super();
		titulo = "";
		descricao = "";
		url = "";
		menu = "";
		unidade = "";
	}
}
