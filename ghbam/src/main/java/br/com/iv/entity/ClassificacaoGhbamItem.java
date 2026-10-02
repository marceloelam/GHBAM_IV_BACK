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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "classificacao_ghbam_item")
public class ClassificacaoGhbamItem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String uuid;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String definicao;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String versao;

    @Column(name = "indicadores_json", columnDefinition = "TEXT")
    private String indicadoresJson;

    @Column(name = "relacionamentos_json", columnDefinition = "TEXT")
    private String relacionamentosJson;

    @Column(name = "criado_por", length = 120)
    private String criadoPor;

    @Column(name = "criado_por_matricula", length = 40)
    private String criadoPorMatricula;

    @Column(name = "revisado_por", length = 120)
    private String revisadoPor;

    @Column(name = "revisado_por_matricula", length = 40)
    private String revisadoPorMatricula;

    @Column(name = "aprovado_por", length = 120)
    private String aprovadoPor;

    @Column(name = "aprovado_por_matricula", length = 40)
    private String aprovadoPorMatricula;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
        if (status == null || status.isBlank()) status = "RASCUNHO";
        if (versao == null || versao.isBlank()) versao = "0.1.0";
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
