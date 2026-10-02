package br.com.iv.entity;

import java.io.Serializable;
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
@Table(name = "plantao")
public class Plantao extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idplantao;

    @ManyToOne
    @JoinColumn(name = "servidor_idservidor", nullable = false)
    private Servidor servidor;

    @Column(length = 30, nullable = false)
    private String unidade;

    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime dataInicio;

    @Column(name = "data_fim")
    private LocalDateTime dataFim;

    @Column(length = 50)
    private String tipo;

    @Column(length = 100)
    private String setor;

    @Column(length = 500)
    private String obs;

    @ManyToOne
    @JoinColumn(name = "cadastrou_idservidor")
    private Servidor cadastrou;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();
}
