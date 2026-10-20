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

// Dispositivo invasivo de uma internacao (V200). A retirada so preenche retirado_*.
@Data
@Entity
@Table(name = "dispositivo_invasivo")
public class DispositivoInvasivo extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 25, nullable = false)
    private String unidade;

    @Column(nullable = false)
    private Integer idbam;

    private Integer idpaciente;

    @Column(length = 30, nullable = false)
    private String tipo;

    @Column(length = 80)
    private String sitio;

    @Column(name = "inserido_em", nullable = false)
    private LocalDateTime inseridoEm;

    @Column(name = "inserido_por", length = 120, nullable = false)
    private String inseridoPor;

    @Column(name = "inserido_por_matricula", length = 40, nullable = false)
    private String inseridoPorMatricula;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm;

    @Column(name = "retirado_em")
    private LocalDateTime retiradoEm;

    @Column(name = "retirado_por", length = 120)
    private String retiradoPor;

    @Column(name = "retirado_por_matricula", length = 40)
    private String retiradoPorMatricula;

    @Column(name = "motivo_retirada", length = 200)
    private String motivoRetirada;

    @PrePersist
    public void prePersist() {
        if (registradoEm == null) {
            registradoEm = LocalDateTime.now();
        }
    }
}
