package br.com.iv.entity;

import java.io.Serializable;
import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "tiss_conta_item")
public class TissContaItem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idtisscontaitem;

    @Column(columnDefinition = "integer default 0")
    private int idtissconta;

    @Column(length = 30)
    private String tipoItem;

    @Column(length = 30)
    private String tabela;

    @Column(length = 30)
    private String codigo;

    @Column(length = 250)
    private String descricao;

    @Column(precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Column(precision = 14, scale = 2)
    private BigDecimal valorUnitario;

    @Column(precision = 14, scale = 2)
    private BigDecimal valorTotal;

    @Column(length = 20)
    private String dataExecucao;

    @Column(length = 80)
    private String executante;

    @Column(length = 20)
    private String conselho;

    @Column(length = 10)
    private String ufConselho;

    @Column(length = 20)
    private String cbo;

    @Column(length = 40)
    private String numeroautorizacao;

    @Column(length = 30)
    private String origem;

    @Column(length = 80)
    private String origemId;

    @Column(length = 20)
    private String status;

    @Column(length = 700)
    private String observacao;
}
