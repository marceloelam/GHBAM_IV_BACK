package br.com.iv.entity;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
@Data
@Entity(name = "prescricao")
public class Prescricao  extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L; // = 6946266081982986113L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idprescricao;
	@Column(length = 100)
	private String medicacao;
	@Column(columnDefinition = "TEXT")
	private String prescricao;
	@Column(columnDefinition = "TEXT")
	private String prescricaopuro;
	@Column(columnDefinition = "TEXT")
	private String observacao2;
	@Column(length = 10)
	private String tprpa;
	@Column(length = 10)
	private String cness;
	@Column(length = 80)
	private String mediccrm;
	@Column(length = 60)
	private String medicoq;
	private int bam;
	private String data;
	@Column(length = 5)
	private String hora;
	@Column(length = 19)
	private String localsolicitante;
	private boolean feito;
	private boolean inativo;
	// Aprazamento
	private String hora1;
	private boolean hora11;
	private String hora2;
	private boolean hora22;
	private String hora3;
	private boolean hora33;
	private String hora4;
	private boolean hora44;
	private String hora5;
	private boolean hora55;
	private String hora6;
	private boolean hora66;
	private String hora7;
	private boolean hora77;
	private String hora8;
	private boolean hora88;
	@Column(length = 300)
	private String medicamentopedido;
	private int retirada;
	private boolean farmfez;

	@Column(columnDefinition = "TEXT")
	private String observacao;

	@Column(columnDefinition = "TEXT")
	private String observacao3;

	@Column(length = 60)
	private String quem;

	@Column(nullable = false)
	private Integer versao = 1;
	@Column(name = "idprescricao_origem")
	private Integer idPrescricaoOrigem;
	@Column(name = "idprescricao_retificacao")
	private Integer idPrescricaoRetificacao;
	@Column(name = "motivo_retificacao", columnDefinition = "TEXT")
	private String motivoRetificacao;
	@Column(name = "matricula_autor", length = 40)
	private String matriculaAutor;

	public Prescricao() {
		super();

		this.cness = "";
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
		Date hora = Calendar.getInstance().getTime();

		this.hora = sdf.format(hora);
		this.prescricao = "";
		this.prescricaopuro = "";
		this.observacao = "";
		this.medicacao = "";
		this.observacao2 = "";
		this.observacao3 = "";
		this.tprpa = "";
		this.medicamentopedido = "";
		this.bam = 0;
		this.data = "";
		this.medicoq = "";
		this.localsolicitante = "";
		this.feito = false;
		this.farmfez = false;
		this.inativo = false;
		hora1 = "";
		hora2 = "";
		hora3 = "";
		hora4 = "";
		hora5 = "";
		hora6 = "";
		hora7 = "";
		hora8 = "";
		retirada = 0;
		hora11 = false;
		hora22 = false;
		hora33 = false;
		hora44 = false;
		hora55 = false;
		hora66 = false;
		hora77 = false;
		hora88 = false;
		mediccrm = "";

	}

	public Prescricao(int idprescricao, String prescricao,  String prescricaopuro, String observacao3, String observacao2, String tprpa,
			String cness, int bam, String data, String hora, String localsolicitante, boolean feito, String hora1,
			boolean hora11, String hora2, boolean hora22, String hora3, boolean hora33, String hora4, boolean hora44,
			String hora5, boolean hora55, String hora6, boolean hora66, String hora7, boolean hora77, String hora8,
			boolean hora88, String observacao, String mediccrm, String medicacao, String medicoq, boolean farmfez) {
		super();
		this.idprescricao = idprescricao;
		this.prescricao = prescricao;
		this.prescricaopuro = prescricaopuro;
		this.medicacao = medicacao;
		this.observacao2 = observacao2;
		this.observacao3 = observacao3;
		this.tprpa = tprpa;
		this.cness = cness;
		this.bam = bam;
		this.data = data;
		this.hora = hora;
		this.farmfez = farmfez;
		this.medicoq = medicoq;
		this.localsolicitante = localsolicitante;
		this.feito = feito;
		this.hora1 = hora1;
		this.hora11 = hora11;
		this.hora2 = hora2;
		this.hora22 = hora22;
		this.hora3 = hora3;
		this.hora33 = hora33;
		this.hora4 = hora4;
		this.hora44 = hora44;
		this.hora5 = hora5;
		this.hora55 = hora55;
		this.hora6 = hora6;
		this.hora66 = hora66;
		this.hora7 = hora7;
		this.hora77 = hora77;
		this.hora8 = hora8;
		this.hora88 = hora88;
		this.observacao = observacao;
		this.mediccrm = mediccrm;
	}

}
