package br.com.iv.entity;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "AnamneseInternacao")
public class AnamneseInternacao extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L; // = -4269330418832724327L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idanamneseinternacao;
	@Column(length = 7200)
	private String frente;
	@Temporal(TemporalType.DATE)
	private Date data;
	@Column(length = 7200)
	private String verso;
	@Column(length = 15)
	private String prontuario;
	@Column(length = 55)
	private String medico;
	@Column(length = 15)
	private String crm;
	private int nbam;


	public AnamneseInternacao() {
		super();
		verso = "";
		frente = "";
		medico = "";
		crm = "";
		nbam = 0;
		prontuario = "";
		try {
			Date data3 = new Date();
			SimpleDateFormat formatador = new SimpleDateFormat("dd/MM/yyyy");
			String dat1 = formatador.format(data3);
			Date dataFormatada = formatador.parse(dat1);
			data = dataFormatada;

		} catch (ParseException e) {
			//
			e.printStackTrace();
		}

	}

}
