package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
@Entity(name = "Atendimento")
public class AtendimentoMedico implements Serializable {

	private static final long serialVersionUID = 1L; // = -3733018379287657044L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idatendimento;
	@Column(length = 2000)
	private String receita;
	@Column(length = 7000)
	private String relato;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
	private Date data;
	@Column(length = 5)
	private String hora;
	@Column(length = 45)
	private String nomedomedico;
	private int bam;
	private int idpaciente;


	public AtendimentoMedico() {
		super();
		limparcampos();
	}

	public void limparcampos() {
		idatendimento = 0;
		receita = "";
		relato = "";
		data = null;
		hora = null;
		nomedomedico = "";
		bam = 0;
		idpaciente = 0;
	}
}
