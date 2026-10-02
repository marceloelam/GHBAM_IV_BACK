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

@Data
@Entity
@Table(name = "cdss_alerta_override")
public class CdssAlertaOverride extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idoverride;

    private Integer idpaciente;
    private Integer idbam;

    @Column(length = 30)
    private String unidade;

    @Column(name = "alerta_codigo", length = 80)
    private String alertaCodigo;

    @Column(name = "alerta_severidade", length = 20)
    private String alertaSeveridade;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(name = "modulo_origem", length = 80)
    private String moduloOrigem;

    @Column(name = "usuario_nome", length = 120)
    private String usuarioNome;

    @Column(name = "usuario_matricula", length = 40)
    private String usuarioMatricula;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }
}
