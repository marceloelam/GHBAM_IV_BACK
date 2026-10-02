package br.com.iv.odontologia;

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
import jakarta.persistence.Transient;
import lombok.Data;

@Data
@Entity
@Table(name = "odontologia_atendimento")
public class OdontologiaAtendimento extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idatendimento;

    private Integer idpaciente;
    private Integer idprontuario;

    @Transient
    private String numeroProntuarioOdontologico;
    private Integer idbam;

    @Column(length = 20)
    private String unidade;

    @Column(length = 20)
    private String status;

    @Column(length = 20)
    private String tipoOdontograma;

    @Column(length = 20)
    private String denticao;

    @Column(columnDefinition = "TEXT")
    private String queixaPrincipal;

    @Column(columnDefinition = "TEXT")
    private String historiaDoencaAtual;

    @Column(columnDefinition = "TEXT")
    private String anamneseJson;

    @Column(columnDefinition = "TEXT")
    private String exameGeral;

    @Column(columnDefinition = "TEXT")
    private String exameExtraoral;

    @Column(columnDefinition = "TEXT")
    private String exameIntraoral;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "evolucoes_json", columnDefinition = "TEXT")
    private String evolucoesJson;

    @Column(name = "periodontais_json", columnDefinition = "TEXT")
    private String periodontaisJson;

    @Column(name = "criado_por", length = 120)
    private String criadoPor;

    @Column(name = "criado_por_matricula", length = 40)
    private String criadoPorMatricula;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = criadoEm;
        if (status == null || status.isBlank()) status = "RASCUNHO";
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
