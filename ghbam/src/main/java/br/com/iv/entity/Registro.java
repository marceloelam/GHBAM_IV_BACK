package br.com.iv.entity;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "Registro")
public class Registro  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idregistro;
	@Column(length = 10)
	private String responsavel;
	@Column(length = 20)
	private String setor;
	@Column(length = 20)
	private String especialidade;
	@Column(length = 20)
	private String convenio;
	@Column(length = 55)
	private String tipochegada;
	@Column(length = 55)
	private String tipoocorrencia;
	@Column(length = 55)
	private String queixaprincipal;
	@Column(length = 35)
	private String causalesao;
	@Column(length = 350)
	private String obsentrada;
	@Column(length = 40)
	private String controle;
	@Column(length = 50)
	private String atendidopor;
	@Column(length = 15)
	private String matriculaatendidopor;
	@Temporal(TemporalType.DATE)
	private Date dtsintomas;
	private int resultadoexamec19;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean eletivo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean eletivointernado;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean resultadolabo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean resultadoexame;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean resultadorx;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean resultadotomo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean resultadomed;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean resultadoenfe;

	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean temlabo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean temrx;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean temtomo;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean temmed;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private boolean temenfe;

	
	
	public Registro() {
		super();
		iniciarAtributos();
	}

	public void iniciarAtributos() {
		responsavel = "PRÓPRIO";
		setor = "";
		especialidade = "";
		convenio = "";
		tipochegada = "";
		tipoocorrencia = "";
		queixaprincipal = "";
		causalesao = "";
		obsentrada = "";
		resultadoexame = false;
		resultadorx = false;
		resultadotomo = false;
		resultadomed = false;
		resultadoenfe = false;
		resultadolabo = false;
		temrx = false;
		temtomo = false;
		temmed = false;
		temenfe = false;
		temlabo = false;
		controle = "";
		atendidopor = "";
		matriculaatendidopor = "";
		resultadoexamec19 = 3;
		eletivo = false;
		eletivointernado = false;
		
		Date data3 = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("dd/MM/yyyy");
		String dat1 = formatador.format(data3);
		Date dataFormatada = null;
		try {
			dataFormatada = formatador.parse(dat1);
		} catch (java.text.ParseException e) {
			
			e.printStackTrace();
		}
		dtsintomas = dataFormatada;

	}

	

}
