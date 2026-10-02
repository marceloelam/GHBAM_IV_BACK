package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "classificacao_ghbam_auditoria")
public class ClassificacaoGhbamAuditoria extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_uuid", nullable = false, length = 80)
    private String itemUuid;

    @Column(nullable = false, length = 40)
    private String evento;

    @Column(name = "ator_nome", length = 120)
    private String atorNome;

    @Column(name = "ator_matricula", length = 40)
    private String atorMatricula;

    @Column(columnDefinition = "TEXT")
    private String detalhe;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;

    @PrePersist
    void prePersist() {
        if (ocorridoEm == null) ocorridoEm = LocalDateTime.now();
    }
}
