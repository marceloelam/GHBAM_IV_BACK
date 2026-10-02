package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import lombok.Data;

@Data
@Entity(name = "EscalaPaciente")
public class EscalaPaciente extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idescalapaciente;

    private String titulo;

    @Column(columnDefinition = "date")
    private LocalDate inicioagenda;

    @Column(columnDefinition = "date")
    private LocalDate fimagenda;

    @Column(length = 15)
    private String dt_nasc;

    @Column(length = 5)
    private String horamarcada;

    @Column(length = 5)
    private String horasaida;

    @Column(length = 30)
    private String setor;

    @ManyToOne(fetch = FetchType.LAZY) // Melhor desempenho em consultas
    @JoinColumn(name = "paciente_idpaciente", nullable = false)
    private Pacientes paciente;

    private String medico;
    private String cadastrou;

    @Column(length = 30)
    private String unidade;

    @Column(length = 600)
    private String obs;

    @Column(length = 600)
    private String obs2;

    @Column(nullable = false)
    private boolean statusevento = false;

    @Column(nullable = false)
    private boolean chegou = false;

    private int agbam;

    @Column(nullable = false)
    private boolean atendido = false;

    @Column(nullable = false)
    private boolean agenda = true;

    private int idDoMedico;

    @Column(length = 1000)
    private String comentario;

    @Column(length = 20)
    private String modalidade = "PRESENCIAL";

    public EscalaPaciente() {
		super();
		iniciarvariavel();
	}

	public void iniciarvariavel() {
		horamarcada = "08:00";
		titulo = "";
		obs = "";
		obs2 = "";
		statusevento = false;
		chegou = false;
		paciente = new Pacientes();
		agbam = 0;
		idDoMedico = 0;
		atendido = false;
		agenda = true;
		comentario = "";
		// inicioagenda = new Date();
		// fimagenda = new Date();
	}


}
