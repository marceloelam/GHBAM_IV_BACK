package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "balanco_hidrico_anotacoes_enfermagem", indexes = {
    @Index(name = "idx_bh_anot_enf_bam_data", columnList = "bam,data_controle")
})
public class BalancoHidricoAnotacaoEnfermagem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 80, nullable = false)
    public String uuid;

    @Column(nullable = false, length = 80)
    public String bam;

    @Column(name = "data_controle", nullable = false)
    public LocalDate data;

    @Column(columnDefinition = "TEXT")
    public String diurno;

    @Column(columnDefinition = "TEXT")
    public String noturno;

    @Column(length = 150)
    public String responsavelDiurno;

    @Column(length = 40)
    public String matriculaDiurno;

    @Column(length = 150)
    public String responsavelNoturno;

    @Column(length = 40)
    public String matriculaNoturno;

    @Column(length = 80)
    public String unidade;

    public LocalDateTime criadoEm;
    public LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        if (uuid == null || uuid.isBlank()) uuid = UUID.randomUUID().toString();
        criadoEm = LocalDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
