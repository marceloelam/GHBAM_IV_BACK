package br.com.iv.entity;

import java.time.LocalDate;
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

/**
 * Plano de acao e linha do tempo do caso do NSP.
 * ACAO_CORRETIVA e ACAO_PREVENTIVA formam o plano; COMENTARIO e MUDANCA_STATUS
 * formam a trilha do que o nucleo fez com a notificacao.
 */
@Data
@Entity
@Table(name = "nsp_evento_acao")
public class NspEventoAcao extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idacao")
    private Long idacao;

    @Column(name = "idevento", nullable = false)
    private Long idevento;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 160)
    private String responsavel;

    private LocalDate prazo;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por", length = 80)
    private String criadoPor;

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
        if (status == null || status.isBlank()) {
            status = "PENDENTE";
        }
    }
}
