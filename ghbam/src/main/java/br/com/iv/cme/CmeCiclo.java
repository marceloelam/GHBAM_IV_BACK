package br.com.iv.cme;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cme_ciclo")
public class CmeCiclo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idciclo")
    public Long idciclo;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "equipamento", nullable = false, length = 80)
    public String equipamento;

    @Column(name = "numerociclo", nullable = false, length = 40)
    public String numeroCiclo;

    @Column(name = "metodo", nullable = false, length = 40)
    public String metodo;

    @Column(name = "datainicio")
    public LocalDateTime dataInicio;

    /** Data local do ciclo: a numeracao reinicia a cada dia por autoclave. */
    @Column(name = "datareferencia")
    public LocalDate dataReferencia;

    @Column(name = "datafim")
    public LocalDateTime dataFim;

    @Column(name = "temperatura", precision = 6, scale = 2)
    public BigDecimal temperatura;

    @Column(name = "pressao", precision = 6, scale = 2)
    public BigDecimal pressao;

    @Column(name = "tempoexposicaomin")
    public Integer tempoExposicaoMin;

    @Column(name = "indicadorfisico", nullable = false, length = 20)
    public String indicadorFisico;

    @Column(name = "indicadorquimico", nullable = false, length = 20)
    public String indicadorQuimico;

    @Column(name = "indicadorbiologico", nullable = false, length = 20)
    public String indicadorBiologico;

    @Column(name = "loteindicadorbiologico", length = 60)
    public String loteIndicadorBiologico;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "motivoreprovacao", columnDefinition = "text")
    public String motivoReprovacao;

    @Column(name = "observacao", columnDefinition = "text")
    public String observacao;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @Column(name = "dataliberacao")
    public LocalDateTime dataLiberacao;

    @Column(name = "usuarioliberacao", length = 80)
    public String usuarioLiberacao;

    @Column(name = "dataatualizacao")
    public LocalDateTime dataAtualizacao;

    @Column(name = "usuarioatualizacao", length = 80)
    public String usuarioAtualizacao;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) {
            dataRegistro = LocalDateTime.now();
        }
        if (dataAtualizacao == null) {
            dataAtualizacao = dataRegistro;
        }
    }
}
