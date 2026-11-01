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
@Table(name = "almox_movimentacao")
public class AlmoxMovimentacao extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmovimentacao")
    public Long idmovimentacao;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "iditem", nullable = false)
    public Long iditem;

    @Column(name = "tipo", nullable = false, length = 20)
    public String tipo;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3)
    public BigDecimal quantidade;

    @Column(name = "custounitario", precision = 14, scale = 4)
    public BigDecimal custoUnitario;

    @Column(name = "saldoapos", nullable = false, precision = 14, scale = 3)
    public BigDecimal saldoApos;

    @Column(name = "documento", length = 60)
    public String documento;

    @Column(name = "fornecedor", length = 160)
    public String fornecedor;

    @Column(name = "lote", length = 60)
    public String lote;

    @Column(name = "validade")
    public LocalDate validade;

    @Column(name = "setor", length = 80)
    public String setor;

    @Column(name = "idrequisicao")
    public Long idrequisicao;

    @Column(name = "justificativa", columnDefinition = "text")
    public String justificativa;

    @Column(name = "usuario", length = 80)
    public String usuario;

    @Column(name = "datamovimentacao")
    public LocalDateTime dataMovimentacao;

    @PrePersist
    public void prePersist() {
        if (dataMovimentacao == null) dataMovimentacao = LocalDateTime.now();
    }
}
