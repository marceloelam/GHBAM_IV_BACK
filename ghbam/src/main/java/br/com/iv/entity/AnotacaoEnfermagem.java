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
@Table(name = "anotacao_enfermagem")
public class AnotacaoEnfermagem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int id;

    private int idpaciente;

    private Integer idbam;

    @Column(length = 40)
    private String prontuario;

    @Column(length = 180)
    private String nomepaciente;

    @Column(length = 20)
    private String unidade;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String anotacao;

    @Column(columnDefinition = "TEXT")
    private String motivo;

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
            this.status = "ATIVA";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
