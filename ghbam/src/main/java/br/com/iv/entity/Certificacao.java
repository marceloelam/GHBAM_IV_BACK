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
@Table(name = "certificacao")
public class Certificacao extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idcertificacao;

    @ManyToOne
    @JoinColumn(name = "servidor_idservidor", nullable = false)
    private Servidor servidor;

    @Column(length = 200, nullable = false)
    private String titulo;

    @Column(length = 200)
    private String instituicao;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(length = 500)
    private String arquivo;

    @Column(length = 30)
    private String unidade;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();
}
