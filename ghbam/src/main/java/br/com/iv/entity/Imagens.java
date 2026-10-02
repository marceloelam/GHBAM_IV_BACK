package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "Imagens")
public class Imagens   extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L; // = -2418324751903389951L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idimagens;
	@Column(length = 13, nullable = false)
	private String procedimento;
	@Column(length = 10, nullable = false)
	private String data;
	@Column(length = 35)
	private String tecnico;
	@Column(length = 12)
	private String cbo;
	@Column(length = 95)
	private String descricao;
	@Column(length = 5)
	private String entrada;
	@Column(length = 5)
	private String liberacao;
	@Column(length = 55)
	private String atendidopor;
	@Column(length = 55)
	private String local;
	@Column(length = 120)
	private String arquivoresultado;
	@Column(length = 600)
	private String Obs;
	@Column(length = 55)
	private String controleid;
	@Column(length = 255)
	private String link;
	@Column(length = 255)
	private String laudo;
	@Column(length = 65)
	private String idunico;
	// Definir padrão das variáveis

	public Imagens() {
		super();
		iniciarvariavel();
	}

	public void iniciarvariavel() {
		entrada = "";
		liberacao = "";
		Obs = "";
		data = "";
		tecnico = "";
		descricao = "";
		cbo = "";
		local = "";
		laudo = "";
		link = "";
		idunico = "";

	}

	
}
