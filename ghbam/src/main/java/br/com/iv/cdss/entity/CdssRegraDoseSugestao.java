package br.com.iv.cdss.entity;

import java.io.Serializable;
import java.math.BigDecimal;
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

// Sugestao de alteracao de limite de dose (V202): PENDENTE, APROVADA ou REJEITADA.
@Data
@Entity
@Table(name = "cdss_regra_dose_sugestao")
public class CdssRegraDoseSugestao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120, nullable = false)
    private String nome;

    @Column(name = "dose_maxima_dia_mg", precision = 12, scale = 3)
    private BigDecimal doseMaximaDiaMg;

    @Column(name = "dose_maxima_mg_kg_dia", precision = 12, scale = 3)
    private BigDecimal doseMaximaMgKgDia;

    @Column(name = "pediatria_mg_kg_dia", precision = 12, scale = 3)
    private BigDecimal pediatriaMgKgDia;

    @Column(name = "sem_teto_fixo", columnDefinition = "TEXT")
    private String semTetoFixo;

    @Column(name = "idade_minima_anos", precision = 6, scale = 2)
    private BigDecimal idadeMinimaAnos;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mudancas;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String justificativa;

    @Column(columnDefinition = "TEXT")
    private String referencia;

    @Column(length = 10, nullable = false)
    private String status;

    @Column(name = "sugerido_por", length = 120, nullable = false)
    private String sugeridoPor;

    @Column(name = "sugerido_por_matricula", length = 40, nullable = false)
    private String sugeridoPorMatricula;

    @Column(name = "sugerido_unidade", length = 25)
    private String sugeridoUnidade;

    @Column(name = "sugerido_em", nullable = false)
    private LocalDateTime sugeridoEm;

    @Column(name = "decidido_por", length = 120)
    private String decididoPor;

    @Column(name = "decidido_por_matricula", length = 40)
    private String decididoPorMatricula;

    @Column(name = "decidido_registro", length = 40)
    private String decididoRegistro;

    @Column(name = "decidido_em")
    private LocalDateTime decididoEm;

    @Column(name = "motivo_decisao", columnDefinition = "TEXT")
    private String motivoDecisao;

    @PrePersist
    public void prePersist() {
        if (sugeridoEm == null) sugeridoEm = LocalDateTime.now();
        if (status == null) status = "PENDENTE";
    }
}
