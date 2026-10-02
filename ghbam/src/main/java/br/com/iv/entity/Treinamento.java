package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "treinamento")
public class Treinamento extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idtreinamento;

    @Column(length = 200, nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "carga_horaria")
    private Integer cargaHoraria;

    @Column(length = 100)
    private String categoria;

    @Column(length = 30)
    private String unidade;

    private boolean obrigatorio;

    @ManyToOne
    @JoinColumn(name = "cadastrou_idservidor")
    private Servidor cadastrou;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();
}
