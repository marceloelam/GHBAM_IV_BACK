package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

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
@Table(name = "cirurgia_agendamento")
public class CirurgiaAgendamento extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idcirurgia;

	@Column(length = 30, nullable = false, unique = true)
	private String numero;

	@Column(nullable = false)
	private int idpaciente;

	@Column(length = 80)
	private String nomepaciente;

	@Column(length = 19)
	private String cnspaciente;

	@Column
	private Integer idbam;

	@Column
	private Integer idinternacao;

	@Column(length = 25, nullable = false)
	private String unidade;

	@Column(length = 15)
	private String cnes;

	@Column
	private Integer idsala;

	@Column(length = 60)
	private String nomesala;

	@Column(name = "data_prevista", nullable = false)
	private LocalDateTime dataPrevista;

	@Column(name = "duracao_prevista_min", nullable = false)
	private int duracaoPrevistaMin;

	@Column(length = 20, nullable = false)
	private String carater;

	@Column(length = 10)
	private String porte;

	@Column(length = 50)
	private String especialidade;

	@Column
	private Integer idcirurgiao;

	@Column(length = 80)
	private String nomecirurgiao;

	@Column(name = "tipo_anestesia", length = 30)
	private String tipoAnestesia;

	@Column(length = 20)
	private String lateralidade;

	@Column(length = 10)
	private String cid;

	@Column(length = 200)
	private String diagnostico;

	@Column(length = 30, nullable = false)
	private String status;

	@Column(name = "necessita_uti", nullable = false)
	private boolean necessitaUti;

	@Column(name = "necessita_hemocomponente", nullable = false)
	private boolean necessitaHemocomponente;

	@Column(name = "reserva_sangue", length = 100)
	private String reservaSangue;

	@Column(columnDefinition = "TEXT")
	private String observacao;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em")
	private LocalDateTime atualizadoEm;

	@Column(name = "usuario_criador", length = 40)
	private String usuarioCriador;

	@Column(name = "usuario_atualizacao", length = 40)
	private String usuarioAtualizacao;

	public CirurgiaAgendamento() {
		super();
		this.criadoEm = LocalDateTime.now();
		this.status = "AGENDADA";
		this.carater = "ELETIVA";
		this.duracaoPrevistaMin = 60;
	}
}
