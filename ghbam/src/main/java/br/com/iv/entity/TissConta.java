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
@Table(name = "tiss_conta")
public class TissConta extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idtissconta;

    @Column(columnDefinition = "integer default 0")
    private int bam;

    @Column(columnDefinition = "integer default 0")
    private int idpaciente;

    @Column(length = 120)
    private String nomepaciente;

    @Column(length = 25)
    private String unidade;

    @Column(length = 20)
    private String competencia;

    @Column(length = 20)
    private String tipo;

    @Column(length = 20)
    private String status;

    @Column(length = 120)
    private String operadora;

    @Column(length = 15)
    private String cnesprestador;

    @Column(length = 20)
    private String registroans;

    @Column(length = 120)
    private String plano;

    @Column(length = 40)
    private String numerocarteira;

    @Column(length = 40)
    private String numeroautorizacao;

    @Column(length = 20)
    private String dataabertura;

    @Column(length = 20)
    private String datafechamento;

    @Column(length = 80)
    private String criadoPor;

    @Column(length = 80)
    private String atualizadoPor;

    @Column(length = 30)
    private String origem;

    @Column(length = 700)
    private String observacao;

    @Column(precision = 14, scale = 2)
    private BigDecimal valorTotal;
}
