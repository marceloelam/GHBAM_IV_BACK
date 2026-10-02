package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "balanco_hidrico_plantoes", indexes = {
    @Index(name = "idx_bh_plantoes_bam_data", columnList = "bam,data_controle")
})
public class BalancoHidricoPlantao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 80, nullable = false)
    public String uuid;

    @Column(nullable = false, length = 80)
    public String bam;

    @Column(name = "data_controle", nullable = false)
    public LocalDate data;

    @Column(name = "data_tela")
    public LocalDate dataTela;

    public LocalDateTime inicioPlantao;
    public LocalDateTime fimPlantao;

    @Column(length = 30)
    public String turno;

    @Column(length = 150)
    public String profissional;

    @Column(length = 40)
    public String matricula;

    public Double totalEntradasMl;
    public Double totalSaidasMl;
    public Double saldoHidricoMl;
    public Double diureseTotalMl;
    public Integer pendenciasAbertas;
    public Double saldoTransportadoMl;

    @Column(columnDefinition = "TEXT")
    public String resumoPlantao;

    @Column(length = 30)
    public String statusFechamento;

    @Column(length = 150)
    public String desbloqueadoPor;

    @Column(columnDefinition = "TEXT")
    public String justificativaDesbloqueio;

    public LocalDateTime desbloqueadoEm;
    public LocalDateTime criadoEm;

    @PrePersist
    void prePersist() {
        if (uuid == null || uuid.isBlank()) uuid = UUID.randomUUID().toString();
        if (statusFechamento == null || statusFechamento.isBlank()) statusFechamento = "FECHADO";
        if (inicioPlantao == null && data != null) inicioPlantao = data.atTime(8, 0);
        if (fimPlantao == null && data != null) fimPlantao = data.plusDays(1).atTime(8, 0);
        criadoEm = LocalDateTime.now();
    }
}
