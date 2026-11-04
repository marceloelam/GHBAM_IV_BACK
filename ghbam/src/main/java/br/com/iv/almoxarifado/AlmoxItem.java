package br.com.iv.almoxarifado;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "almox_item")
public class AlmoxItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iditem")
    public Long iditem;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "codigo", length = 40)
    public String codigo;

    @Column(name = "descricao", nullable = false, length = 160)
    public String descricao;

    @Column(name = "unidademedida", nullable = false, length = 20)
    public String unidadeMedida;

    @Column(name = "grupo", nullable = false, length = 30)
    public String grupo;

    @Column(name = "estoqueminimo", nullable = false, precision = 14, scale = 3)
    public BigDecimal estoqueMinimo;

    @Column(name = "estoquemaximo", precision = 14, scale = 3)
    public BigDecimal estoqueMaximo;

    @Column(name = "saldo", nullable = false, precision = 14, scale = 3)
    public BigDecimal saldo;

    @Column(name = "customedio", nullable = false, precision = 14, scale = 4)
    public BigDecimal custoMedio;

    @Column(name = "ativo", nullable = false)
    public Boolean ativo;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @Column(name = "dataatualizacao")
    public LocalDateTime dataAtualizacao;

    @Column(name = "usuarioatualizacao", length = 80)
    public String usuarioAtualizacao;

    /** Validade mais proxima do saldo atual (calculada por FIFO nas entradas; nao vai para o banco). */
    @Transient
    public LocalDate proximaValidade;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
        if (saldo == null) saldo = BigDecimal.ZERO;
        if (custoMedio == null) custoMedio = BigDecimal.ZERO;
        if (estoqueMinimo == null) estoqueMinimo = BigDecimal.ZERO;
        if (ativo == null) ativo = Boolean.TRUE;
    }
}
