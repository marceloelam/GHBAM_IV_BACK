package br.com.iv.cdss.entity;

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

// Trilha de alteracoes e validacoes dos limites de dose (V201). Nunca apagada.
@Data
@Entity
@Table(name = "cdss_regra_dose_historico")
public class CdssRegraDoseHistorico extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120, nullable = false)
    private String nome;

    @Column(length = 20, nullable = false)
    private String acao;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String detalhe;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(length = 120, nullable = false)
    private String por;

    @Column(name = "por_matricula", length = 40, nullable = false)
    private String porMatricula;

    @Column(name = "registro_profissional", length = 40)
    private String registroProfissional;

    @Column(name = "por_unidade", length = 25)
    private String porUnidade;

    @Column(nullable = false)
    private LocalDateTime em;

    @PrePersist
    public void prePersist() {
        if (em == null) {
            em = LocalDateTime.now();
        }
    }
}
