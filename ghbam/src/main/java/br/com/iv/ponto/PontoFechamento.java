package br.com.iv.ponto;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Mes fechado para a folha: trava ajustes, anulacoes e ocorrencias daquele mes na unidade. */
@Entity
@Table(name = "rh_ponto_fechamento")
public class PontoFechamento extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idfechamento")
    public Long idfechamento;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "mes", nullable = false, length = 7)
    public String mes;

    @Column(name = "fechadopor", length = 80)
    public String fechadoPor;

    @Column(name = "datafechamento")
    public LocalDateTime dataFechamento;

    @Column(name = "ativo", nullable = false)
    public Boolean ativo;

    @Column(name = "reabertopor", length = 80)
    public String reabertoPor;

    @Column(name = "datareabertura")
    public LocalDateTime dataReabertura;

    @Column(name = "motivoreabertura", columnDefinition = "text")
    public String motivoReabertura;

    @PrePersist
    public void prePersist() {
        if (dataFechamento == null) dataFechamento = LocalDateTime.now();
        if (ativo == null) ativo = Boolean.TRUE;
    }
}
