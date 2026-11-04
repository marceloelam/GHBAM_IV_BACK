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
@Table(name = "prescricao_item_estruturado")
public class PrescricaoItemEstruturado extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int iditem;

    private Integer idprescricao;

    private Integer idpaciente;

    private int idbam;

    @Column(length = 20)
    private String unidade;

    @Column(length = 20)
    private String status;

    @Column(name = "tipo_item", length = 40)
    private String tipoItem;

    @Column(length = 180)
    private String medicamento;

    @Column(length = 80)
    private String dose;

    @Column(length = 60)
    private String via;

    @Column(length = 120)
    private String frequencia;

    @Column(length = 80)
    private String duracao;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    /** MEDICACAO (SPA), MEDICACAO2 (Maternidade) ou MEDICACAO3 (Pediatria); vazio = SPA. */
    @Column(name = "setor_execucao", length = 20)
    private String setorExecucao;

    @Column(name = "prescrito_por", length = 120)
    private String prescritoPor;

    @Column(name = "prescrito_por_matricula", length = 40)
    private String prescritoPorMatricula;

    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    @Column(name = "atualizado_por_matricula", length = 40)
    private String atualizadoPorMatricula;

    @Column(name = "suspenso_por", length = 120)
    private String suspensoPor;

    @Column(name = "suspenso_por_matricula", length = 40)
    private String suspensoPorMatricula;

    @Column(name = "motivo_suspensao", columnDefinition = "TEXT")
    private String motivoSuspensao;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "suspenso_em")
    private LocalDateTime suspensoEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.criadoEm = now;
        this.atualizadoEm = now;
        if (this.status == null || this.status.isBlank()) {
            this.status = "ATIVO";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
