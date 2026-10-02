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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "notificacao_sinan", indexes = {
        @Index(name = "idx_notificacao_sinan_unidade_data", columnList = "unidade,dataNotificacao"),
        @Index(name = "idx_notificacao_sinan_paciente", columnList = "idpaciente")
})
public class NotificacaoSinan extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String tipo;

    private Integer idpaciente;
    private Integer idbam;

    @Column(length = 20)
    private String unidade;

    @Column(length = 180)
    private String nomePaciente;

    private LocalDateTime dataNotificacao;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String dados;

    @Column(length = 120)
    private String criadoPor;

    @Column(length = 40)
    private String criadoPorMatricula;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 20)
    private String periodicidade;

    @Column(name = "prazo_notificacao")
    private LocalDateTime prazoNotificacao;

    @Column(name = "encaminhado_em")
    private LocalDateTime encaminhadoEm;

    @Column(name = "encaminhado_por", length = 120)
    private String encaminhadoPor;

    @Column(name = "recebido_em")
    private LocalDateTime recebidoEm;

    @Column(name = "recebido_por", length = 120)
    private String recebidoPor;

    @Column(name = "transmitido_em")
    private LocalDateTime transmitidoEm;

    @Column(name = "transmitido_por", length = 120)
    private String transmitidoPor;

    @Column(name = "referencia_transmissao", length = 160)
    private String referenciaTransmissao;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
        if (dataNotificacao == null) dataNotificacao = agora;
        if (status == null || status.isBlank()) status = "RASCUNHO";
    }

    @PreUpdate
    void preUpdate() { atualizadoEm = LocalDateTime.now(); }
}
