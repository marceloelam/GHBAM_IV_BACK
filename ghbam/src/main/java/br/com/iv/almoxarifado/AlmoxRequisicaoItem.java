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
@Table(name = "almox_requisicao_item")
public class AlmoxRequisicaoItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idrequisicaoitem")
    public Long idrequisicaoitem;

    @Column(name = "idrequisicao", nullable = false)
    public Long idrequisicao;

    @Column(name = "iditem", nullable = false)
    public Long iditem;

    @Column(name = "quantidadesolicitada", nullable = false, precision = 14, scale = 3)
    public BigDecimal quantidadeSolicitada;

    @Column(name = "quantidadeatendida", precision = 14, scale = 3)
    public BigDecimal quantidadeAtendida;
}
