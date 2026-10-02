package br.com.iv.entity;

import java.io.Serializable;
import java.math.BigDecimal;
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
@Table(name = "prescricao_item_ciclo_evento")
public class PrescricaoItemCicloEvento extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idevento;

    @Column(name = "iditem", nullable = false)
    private int iditem;

    @Column(name = "idbam", nullable = false)
    private int idbam;

    @Column(name = "idpaciente")
    private Integer idpaciente;

    @Column(name = "idprescricao")
    private Integer idprescricao;

    @Column(name = "unidade", length = 20)
    private String unidade;

    @Column(name = "tipo_evento", length = 30, nullable = false)
    private String tipoEvento;

    @Column(name = "status_evento", length = 30, nullable = false)
    private String statusEvento;

    @Column(name = "data_hora_prevista")
    private LocalDateTime dataHoraPrevista;

    @Column(name = "data_hora_realizada")
    private LocalDateTime dataHoraRealizada;

    @Column(name = "quantidade", precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "dose_administrada", length = 80)
    private String doseAdministrada;

    @Column(name = "responsavel", length = 120)
    private String responsavel;

    @Column(name = "responsavel_matricula", length = 40)
    private String responsavelMatricula;

    @Column(name = "observacao", columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (this.statusEvento == null || this.statusEvento.isBlank()) {
            this.statusEvento = "REGISTRADO";
        }
    }
}
