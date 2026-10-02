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
@Table(name = "classificacao_ghbam_plano_item")
public class ClassificacaoGhbamPlanoItem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String uuid;

    @Column(nullable = false)
    private Integer bam;

    private Integer idpaciente;

    @Column(length = 60)
    private String unidade;

    @Column(name = "item_uuid", nullable = false, length = 80)
    private String itemUuid;

    @Column(name = "item_codigo", nullable = false, length = 40)
    private String itemCodigo;

    @Column(name = "item_versao", nullable = false, length = 20)
    private String itemVersao;

    @Column(name = "item_tipo", nullable = false, length = 40)
    private String itemTipo;

    @Column(name = "item_titulo", nullable = false, length = 180)
    private String itemTitulo;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(length = 20)
    private String avaliacao;

    @Column(name = "avaliacao_observacao", columnDefinition = "TEXT")
    private String avaliacaoObservacao;

    @Column(name = "avaliado_por", length = 120)
    private String avaliadoPor;

    @Column(name = "avaliado_por_matricula", length = 40)
    private String avaliadoPorMatricula;

    @Column(name = "avaliado_em")
    private LocalDateTime avaliadoEm;

    @Column(name = "incluido_por", length = 120)
    private String incluidoPor;

    @Column(name = "incluido_por_matricula", length = 40)
    private String incluidoPorMatricula;

    @Column(name = "incluido_em", nullable = false)
    private LocalDateTime incluidoEm;

    @Column(name = "encerrado_por", length = 120)
    private String encerradoPor;

    @Column(name = "encerrado_por_matricula", length = 40)
    private String encerradoPorMatricula;

    @Column(name = "encerrado_em")
    private LocalDateTime encerradoEm;

    @Column(name = "motivo_encerramento", columnDefinition = "TEXT")
    private String motivoEncerramento;

    @PrePersist
    void prePersist() {
        if (incluidoEm == null) incluidoEm = LocalDateTime.now();
        if (status == null || status.isBlank()) status = "ATIVO";
    }
}
