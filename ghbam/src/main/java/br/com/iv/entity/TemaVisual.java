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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(name = "tema_visual", uniqueConstraints = @UniqueConstraint(name = "uk_tema_visual_unidade", columnNames = "unidade"))
public class TemaVisual extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idtema_visual")
    private Long idTemaVisual;

    @Column(length = 30, nullable = false)
    private String unidade;

    @Column(length = 7, nullable = false)
    private String primaria;

    @Column(length = 7, nullable = false)
    private String secundaria;

    @Column(nullable = false)
    private Boolean gradiente = true;

    @Column(name = "papeis_json", columnDefinition = "TEXT")
    private String papeisJson;

    @Column(length = 120)
    private String usuario;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
