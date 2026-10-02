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
@Entity(name = "EvolucaoPacienteReceituario")
public class EvolucaoPacienteReceituario extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = -8971895080490981244L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int idevolucaoreceituario;
	private LocalDate data;
	@Column(columnDefinition = "TEXT")
	private String relato;
	private int nbam;
	@Column(length = 5) // nullable=false - Não pode ser nulo
	private String horaev;
	private String medicocrm;
	private String unidade;

	public EvolucaoPacienteReceituario() {
		super();
		relato = "";
		medicocrm = "";
		unidade = "";
		nbam = 0;
		data = LocalDate.now();
		Calendar data = Calendar.getInstance();
		int hora = data.get(Calendar.HOUR_OF_DAY);
		int min = data.get(Calendar.MINUTE);
		// int seg = data.get(Calendar.SECOND);

		horaev = hora + ":" + min;

	}

}
