package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;

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
@Table(name = "treinamento_participante")
public class TreinamentoParticipante extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "treinamento_idtreinamento", nullable = false)
    private Treinamento treinamento;

    @ManyToOne
    @JoinColumn(name = "servidor_idservidor", nullable = false)
    private Servidor servidor;

    @Column(length = 30)
    private String status = "PENDENTE";

    @Column(name = "data_conclusao")
    private LocalDate dataConclusao;

    @Column(length = 200)
    private String certificado;

    @Column(length = 500)
    private String obs;
}
