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

// Passagem de plantao por leito no formato SBAR (V199). Nunca editada: uma nova
// passagem e uma nova linha; o recebimento so preenche as colunas recebido_*.
@Data
@Entity
@Table(name = "passagem_plantao")
public class PassagemPlantao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 25, nullable = false)
    private String unidade;

    @Column(nullable = false)
    private Integer idbam;

    private Integer idpaciente;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String situacao;

    @Column(columnDefinition = "TEXT")
    private String historico;

    @Column(columnDefinition = "TEXT")
    private String avaliacao;

    @Column(columnDefinition = "TEXT")
    private String recomendacao;

    @Column(name = "entregue_por", length = 120, nullable = false)
    private String entreguePor;

    @Column(name = "entregue_por_matricula", length = 40, nullable = false)
    private String entreguePorMatricula;

    @Column(name = "entregue_em", nullable = false)
    private LocalDateTime entregueEm;

    @Column(name = "recebido_por", length = 120)
    private String recebidoPor;

    @Column(name = "recebido_por_matricula", length = 40)
    private String recebidoPorMatricula;

    @Column(name = "recebido_em")
    private LocalDateTime recebidoEm;

    @PrePersist
    public void prePersist() {
        if (entregueEm == null) {
            entregueEm = LocalDateTime.now();
        }
    }
}
