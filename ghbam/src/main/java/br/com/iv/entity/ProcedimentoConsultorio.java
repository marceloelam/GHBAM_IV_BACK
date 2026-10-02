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
@Table(name = "procedimentoconsultorio")
public class ProcedimentoConsultorio extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private int idprocedimentoconsultorio;

    @Column(length = 25)
    private String unidade;

    @Column(length = 30)
    private String codigo;

    @Column(length = 120)
    private String nome;

    @Column(length = 80)
    private String especialidade;

    @Column(name = "tipocobranca", length = 20)
    private String tipoCobranca;

    @Column(name = "valorpadrao", precision = 12, scale = 2)
    private BigDecimal valorPadrao;

    @Column(name = "valorrepassepadrao", precision = 12, scale = 2)
    private BigDecimal valorRepassePadrao;

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
