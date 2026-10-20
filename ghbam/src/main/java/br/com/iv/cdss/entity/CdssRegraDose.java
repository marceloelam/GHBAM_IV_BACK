package br.com.iv.cdss.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// Valor vigente e estado de validacao de um limite de dose do CDSS (V201).
// Sem linha = vale o arquivo base, nao validado.
@Data
@Entity
@Table(name = "cdss_regra_dose")
public class CdssRegraDose extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean alterado;

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

    @Column(nullable = false)
    private boolean validado;

    @Column(name = "validado_por", length = 120)
    private String validadoPor;

    @Column(name = "validado_por_matricula", length = 40)
    private String validadoPorMatricula;

    @Column(name = "validado_registro", length = 40)
    private String validadoRegistro;

    @Column(name = "validado_em")
    private LocalDateTime validadoEm;

    @Column(name = "validado_unidade", length = 25)
    private String validadoUnidade;

    /** BASE = veio do arquivo; MANUAL = incluido pela tela (V204). */
    @Column(length = 10, nullable = false)
    private String origem = "BASE";

    /** Sinonimos/nomes comerciais dos incluidos pela tela, separados por ";". */
    @Column(columnDefinition = "TEXT")
    private String sinonimos;

    /** Exclusao logica: sem limite de dose no CDSS ate ser restaurado. */
    @Column(nullable = false)
    private boolean excluido;

    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    @Column(name = "atualizado_por_matricula", length = 40)
    private String atualizadoPorMatricula;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
