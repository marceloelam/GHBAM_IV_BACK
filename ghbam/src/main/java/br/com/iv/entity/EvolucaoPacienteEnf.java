package br.com.iv.entity;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
@Data
@Entity(name = "EvolucaoPacienteEnf")
public class EvolucaoPacienteEnf extends PanacheEntityBase implements Serializable {

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
	@Column(length = 60)
	private String enfermeiro;
	private int nbam;
	@Column(length = 5)
	private String hora;
	@Column(length = 15)
	private String quem;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedido;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidolimpo;
	@Column(length = 40) // nullable=false - Não pode ser nulo
	private String local;
	@Column(length = 65) // nullable=false - Não pode ser nulo
	private String medico;
	@Column(length = 25) // nullable=false - Não pode ser nulo
	private String crm;
	@Column(length = 2000) // nullable=false - Não pode ser nulo
	private String pedidodomed;
	@Column(length = 35) // nullable=false - Não pode ser nulo
	private String unidade;

	@Column(nullable = false)
	private Integer versao = 1;
	@Column(name = "idevolucao_origem")
	private Integer idEvolucaoOrigem;
	@Column(name = "idevolucao_retificacao")
	private Integer idEvolucaoRetificacao;
	@Column(name = "motivo_retificacao", columnDefinition = "TEXT")
	private String motivoRetificacao;
	@Column(name = "matricula_autor", length = 40)
	private String matriculaAutor;

	public EvolucaoPacienteEnf() {
		super();
		enfermaria = "";
		enfermeiro = "";
		relato = "";
		prontuario = "";
		pedido = "";
		quem = "";
		pedidolimpo = "";
		pedidodomed = "";
		medico = "";
		crm = "";
		local = "";
		nbam = 0;

		unidade = "";
		try {
			Date data3 = new Date();
			SimpleDateFormat formatador = new SimpleDateFormat("dd/MM/yyyy");
			String dat1 = formatador.format(data3);
			formatador.parse(dat1);

			// data/hora atual
			LocalDateTime agora = LocalDateTime.now();

			// formatar a data
			// DateTimeFormatter formatterData = DateTimeFormatter.ofPattern("dd/MM/uuuu");
			// String dataFormatada = formatterData.format(agora);

			// formatar a hora
			DateTimeFormatter formatterHora = DateTimeFormatter.ofPattern("HH:mm");
			hora = formatterHora.format(agora);

		} catch (ParseException e) {
			//
			e.printStackTrace();
		}

	}

}
