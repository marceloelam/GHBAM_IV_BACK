package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
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
@Table(name = "sae_enfermagem")
public class SaeEnfermagem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 80)
    private String uuid;

    @Column(length = 30)
    private String status;

    @Column(name = "tipo_sae", nullable = false, length = 20)
    private String tipoSae = "GERAL";

    @Column(nullable = false)
    private Integer versao = 1;

    @Column(name = "uuid_origem", length = 80)
    private String uuidOrigem;

    @Column(name = "motivo_retificacao", columnDefinition = "TEXT")
    private String motivoRetificacao;

    @Column(name = "data_sae", nullable = false)
    private LocalDate data;

    private Integer idpaciente;

    private Integer idbam;

    @Column(length = 40)
    private String prontuario;

    @Column(length = 180)
    private String nomepaciente;

    @Column(length = 30)
    private String unidade;

    @Column(name = "paciente_json", columnDefinition = "TEXT")
    private String pacienteJson;

    @Column(name = "historico_json", columnDefinition = "TEXT")
    private String historicoJson;

    @Column(name = "diagnosticos_json", columnDefinition = "TEXT")
    private String diagnosticosJson;

    @Column(name = "cuidados_json", columnDefinition = "TEXT")
    private String cuidadosJson;

    @Column(name = "prescricoes_json", columnDefinition = "TEXT")
    private String prescricoesJson;

    @Column(name = "evolucoes_json", columnDefinition = "TEXT")
    private String evolucoesJson;

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
            this.status = "EM_ANDAMENTO";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
