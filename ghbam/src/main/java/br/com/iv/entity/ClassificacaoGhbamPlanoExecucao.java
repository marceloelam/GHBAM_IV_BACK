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
@Table(name = "classificacao_ghbam_plano_execucao")
public class ClassificacaoGhbamPlanoExecucao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plano_item_uuid", nullable = false, length = 80)
    private String planoItemUuid;

    @Column(nullable = false)
    private Integer bam;

    @Column(nullable = false, length = 20)
    private String situacao;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "executado_por", length = 120)
    private String executadoPor;

    @Column(name = "executado_por_matricula", length = 40)
    private String executadoPorMatricula;

    @Column(name = "executado_em", nullable = false)
    private LocalDateTime executadoEm;

    @PrePersist
    void prePersist() {
        if (executadoEm == null) executadoEm = LocalDateTime.now();
    }
}
