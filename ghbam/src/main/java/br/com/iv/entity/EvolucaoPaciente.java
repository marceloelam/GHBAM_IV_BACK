package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Calendar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "EvolucaoPaciente")
public class EvolucaoPaciente extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = -8971895080490981244L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idevolucao;
	@Column(length = 30)
	private String leito;
	private LocalDate data;
	@Column(length = 35)
	private String prontuario;
	@Column(columnDefinition = "TEXT")
	private String relato;
	@Column(length = 30)
	private String enfermaria;
	private int nbam;
	private boolean alta;
	private boolean inativo;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedido;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidolimpo;
	@Column(length = 20) // nullable=false - Não pode ser nulo
	private String local;
	@Column(length = 65) // nullable=false - Não pode ser nulo
	private String medico;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String crm;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidodomed;
	@Column(length = 5) // nullable=false - Não pode ser nulo
	private String horaev;
	@Column(length = 35) // nullable=false - Não pode ser nulo
	private int qtde;
	private String unidade;

	public EvolucaoPaciente() {
		super();
		enfermaria = "";
		relato = "";
		prontuario = "";
		alta = false;
		pedido = "";
		pedidolimpo = "";
		pedidodomed = "";
		medico = "";
		crm = "";
		local = "";
		nbam = 0;
		qtde = 0;
        inativo=false;
		unidade = "";

		data = LocalDate.now();

		Calendar data = Calendar.getInstance();
		int hora = data.get(Calendar.HOUR_OF_DAY);
		int min = data.get(Calendar.MINUTE);
		// int seg = data.get(Calendar.SECOND);

		horaev = hora + ":" + min;

	}

}
