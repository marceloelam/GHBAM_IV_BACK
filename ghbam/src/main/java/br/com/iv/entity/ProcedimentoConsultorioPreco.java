package br.com.iv.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "procedimentoconsultoriopreco")
public class ProcedimentoConsultorioPreco extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private int idprocedimentoconsultoriopreco;

    @Column(length = 25)
    private String unidade;

    @Column(name = "idprocedimentoconsultorio", columnDefinition = "integer default 0")
    private int idprocedimentoConsultorio;

    @Column(name = "tipocobranca", length = 20)
    private String tipoCobranca;

    @Column(length = 80)
    private String convenio;

    @Column(length = 80)
    private String plano;

    @Column(precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "valorrepasse", precision = 12, scale = 2)
    private BigDecimal valorRepasse;

    @Column(length = 1)
    private String ativo;

    @Column(name = "criadoem")
    private LocalDateTime criadoEm;

    @Column(name = "criadopor", length = 80)
    private String criadoPor;

    @Column(name = "atualizadoem")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizadopor", length = 80)
    private String atualizadoPor;
}
