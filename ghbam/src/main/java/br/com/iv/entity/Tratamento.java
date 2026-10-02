package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
@Table(name = "tratamento")
public class Tratamento  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idtratamento;
	@Column(length = 1500)
	private String exames_solicitados;
	@Column(length = 650)
	private String diagnostico_provisorio;
	@Column(length = 1500)
	private String prescricao_inicial;
	@Column(length = 900)
	private String evolucao_prescricao;
	@Column(length = 105)
	private String destino;
	@Column(length = 105)
	private String encaminhamento;
	@Column(length = 10)
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 650)
	private String obs_saida;
	@Column(length = 1000)
	private String parecer_especialista;
	@Column(length = 650)
	private String obs_enfermagem;
	@Column(length = 20)
	private String tipo_alta;
	private int tratado;
	@Column(length = 40)
	private String controle;
	@Column(length = 100)
	private String medicoecrm;


	public Tratamento() {
		super();
		iniciarAtributos();
	}

	public void iniciarAtributos() {

	//	LocalDate data3 = LocalDate.now();
	//	DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
	//	data = data3.format(formatador);

		exames_solicitados = "";
		diagnostico_provisorio = "";
		prescricao_inicial = "";
		evolucao_prescricao = "";
		destino = "";
		encaminhamento = "";
		// data = "";
		hora = "";
		obs_saida = "";
		parecer_especialista = "";
		obs_enfermagem = "";
		tipo_alta = "-";
		tratado = 0;
		controle = "";
		medicoecrm = "";
	}

	

}
