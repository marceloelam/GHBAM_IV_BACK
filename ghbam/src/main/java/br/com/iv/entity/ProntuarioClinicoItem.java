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
@Table(name = "prontuario_clinico_item")
public class ProntuarioClinicoItem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int iditem;

    private int idpaciente;

    private Integer idbam;

    @Column(length = 20)
    private String unidade;

    @Column(length = 30)
    private String tipo;

    @Column(length = 20)
    private String status;

    @Column(length = 40)
    private String codigo;

    @Column(length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_por", length = 120)
    private String criadoPor;

    @Column(name = "criado_por_matricula", length = 40)
    private String criadoPorMatricula;

    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    @Column(name = "atualizado_por_matricula", length = 40)
    private String atualizadoPorMatricula;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

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
