package br.com.iv.entity;

import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "notificacao_sinan_historico", indexes = {
        @Index(name = "idx_sinan_historico_notificacao_data", columnList = "notificacao_id,criado_em")
})
public class NotificacaoSinanHistorico extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notificacao_id", nullable = false)
    private Long notificacaoId;

    @Column(nullable = false, length = 30)
    private String acao;

    @Column(name = "status_anterior", length = 20)
    private String statusAnterior;

    @Column(name = "status_novo", nullable = false, length = 20)
    private String statusNovo;

    @Column(length = 120)
    private String usuario;

    @Column(length = 500)
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }
}
