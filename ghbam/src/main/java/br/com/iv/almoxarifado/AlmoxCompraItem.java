package br.com.iv.almoxarifado;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "almox_compra_item")
public class AlmoxCompraItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcompraitem")
    public Long idcompraitem;

    @Column(name = "idcompra", nullable = false)
    public Long idcompra;

    @Column(name = "iditem", nullable = false)
    public Long iditem;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3)
    public BigDecimal quantidade;

    @Column(name = "custounitario", precision = 14, scale = 4)
    public BigDecimal custoUnitario;

    @Column(name = "quantidaderecebida", precision = 14, scale = 3)
    public BigDecimal quantidadeRecebida;
}
