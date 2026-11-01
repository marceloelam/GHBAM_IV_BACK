package br.com.iv.almoxarifado;

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
@Table(name = "almox_patrimonio")
public class AlmoxPatrimonio extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpatrimonio")
    public Long idpatrimonio;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "tombamento", nullable = false, length = 40)
    public String tombamento;

    @Column(name = "descricao", nullable = false, length = 160)
    public String descricao;

    @Column(name = "categoria", nullable = false, length = 30)
    public String categoria;

    @Column(name = "marca", length = 80)
    public String marca;

    @Column(name = "modelo", length = 80)
    public String modelo;

    @Column(name = "numeroserie", length = 80)
    public String numeroSerie;

    @Column(name = "idfornecedor")
    public Long idfornecedor;

    @Column(name = "notafiscal", length = 60)
    public String notaFiscal;

    @Column(name = "dataaquisicao")
    public LocalDate dataAquisicao;

    @Column(name = "valoraquisicao", precision = 14, scale = 2)
    public BigDecimal valorAquisicao;

    @Column(name = "setor", length = 80)
    public String setor;

    @Column(name = "responsavel", length = 160)
    public String responsavel;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "databaixa")
    public LocalDateTime dataBaixa;

    @Column(name = "motivobaixa", columnDefinition = "text")
    public String motivoBaixa;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
    }
}
