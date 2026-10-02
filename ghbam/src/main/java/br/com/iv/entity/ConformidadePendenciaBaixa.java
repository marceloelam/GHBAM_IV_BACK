package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

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
@Table(name = "conformidade_pendencia_baixa")
public class ConformidadePendenciaBaixa extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idbaixa")
    private Integer idbaixa;

    private Integer idbam;

    @Column(length = 30)
    private String unidade;

    @Column(name = "chave_pendencia", length = 220)
    private String chavePendencia;

    @Column(columnDefinition = "TEXT")
    private String titulo;

    @Column(length = 40)
    private String tipo;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(length = 120)
    private String responsavel;

    @Column(name = "responsavel_matricula", length = 40)
    private String responsavelMatricula;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}
