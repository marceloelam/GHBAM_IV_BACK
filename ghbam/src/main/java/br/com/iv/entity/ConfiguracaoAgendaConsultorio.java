package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "configuracaoagendaconsultorio")
public class ConfiguracaoAgendaConsultorio extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private int idconfiguracaoagendaconsultorio;

    @Column(length = 25)
    private String unidade;

    @Column(length = 100)
    private String profissional;

    @Column(length = 80)
    private String especialidade;

    @Column(length = 80)
    private String sala;

    @Column(name = "diasemana", length = 20)
    private String diaSemana;

    @Column(name = "dataagenda")
    private LocalDate dataAgenda;

    @Column(name = "horainicio")
    private LocalTime horaInicio;

    @Column(name = "horafim")
    private LocalTime horaFim;

    @Column(name = "duracaominutos", columnDefinition = "integer default 30")
    private int duracaoMinutos;

    @Column(name = "limiteencaixes", columnDefinition = "integer default 0")
    private int limiteEncaixes;

    @Column(name = "intervaloretornodias", columnDefinition = "integer default 30")
    private int intervaloRetornoDias;

    @Column(name = "permiteencaixe", length = 1)
    private String permiteEncaixe;

    @Column(length = 1)
    private String ativo;

    @Column(length = 500)
    private String observacao;

    @Column(name = "criadoem")
    private LocalDateTime criadoEm;

    @Column(name = "criadopor", length = 80)
    private String criadoPor;

    @Column(name = "atualizadoem")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizadopor", length = 80)
    private String atualizadoPor;
}
