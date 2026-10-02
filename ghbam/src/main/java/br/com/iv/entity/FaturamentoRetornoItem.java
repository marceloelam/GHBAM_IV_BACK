package br.com.iv.entity;

import java.io.Serializable;

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
@Table(name = "faturamento_retorno_item")
public class FaturamentoRetornoItem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idretornoitem;

    @Column(nullable = false)
    private int idretorno;

    private Integer idafaturar;

    @Column(name = "codigo_externo", length = 60)
    private String codigoExterno;

    @Column(name = "valor_apresentado")
    private double valorApresentado;

    @Column(name = "valor_aprovado")
    private double valorAprovado;

    @Column(length = 20, nullable = false)
    private String resultado;

    @Column(length = 300)
    private String motivo;

    @Column(name = "codigo_critica", length = 40)
    private String codigoCritica;
}
