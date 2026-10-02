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
@Table(
    name = "prescricao_aprazamento_checagem",
    uniqueConstraints = @UniqueConstraint(name = "uk_prescricao_aprazamento_horario", columnNames = {"idprescricao", "indice_horario"})
)
public class PrescricaoAprazamentoChecagem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idaprazamentochecagem;

    @Column(name = "idprescricao", nullable = false)
    private int idprescricao;

    @Column(name = "idbam")
    private Integer idbam;

    @Column(name = "idpaciente")
    private Integer idpaciente;

    @Column(name = "indice_horario", nullable = false)
    private int indiceHorario;

    @Column(name = "horario_aprazado", length = 5, nullable = false)
    private String horarioAprazado;

    @Column(name = "observacao_aprazamento", columnDefinition = "TEXT")
    private String observacaoAprazamento;

    @Column(name = "aprazado_por", length = 120)
    private String aprazadoPor;

    @Column(name = "aprazado_por_matricula", length = 40)
    private String aprazadoPorMatricula;

    @Column(name = "aprazado_em")
    private LocalDateTime aprazadoEm;

    @Column(name = "checado")
    private boolean checado;

    @Column(name = "checado_em")
    private LocalDateTime checadoEm;

    @Column(name = "checado_por", length = 120)
    private String checadoPor;

    @Column(name = "checado_por_matricula", length = 40)
    private String checadoPorMatricula;

    @Column(name = "observacao_checagem", columnDefinition = "TEXT")
    private String observacaoChecagem;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        if (this.aprazadoEm == null) {
            this.aprazadoEm = agora;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
