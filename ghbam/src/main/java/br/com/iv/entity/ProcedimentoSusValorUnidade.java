package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(
    name = "s_proced_unidade_valor",
    uniqueConstraints = @UniqueConstraint(columnNames = {"unidade", "pa_id", "pa_dv"})
)
public class ProcedimentoSusValorUnidade extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idprocedimentovalorunidade;

    @Column(length = 20, nullable = false)
    private String unidade;

    @Column(length = 9, nullable = false)
    private String pa_id;

    @Column(length = 1, nullable = false)
    private String pa_dv;

    private double pa_sa;
    private double pa_sp;
    private double pa_sh;

    @Column(name = "usarvalorpersonalizado", nullable = false)
    private boolean usarValorPersonalizado;

    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
