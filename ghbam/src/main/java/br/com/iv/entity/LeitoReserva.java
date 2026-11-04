package br.com.iv.entity;

import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** Reserva de leito pelo NIR (V238). Gravada por LeitoReservaService. */
@Data
@Entity(name = "LeitoReserva")
@Table(name = "leito_reserva")
public class LeitoReserva extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int idleito;

    @Column(nullable = false)
    private int bam;

    @Column(nullable = false, length = 35)
    private String unidade;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "criada_por", nullable = false, length = 80)
    private String criadaPor;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "encerrada_em")
    private LocalDateTime encerradaEm;

    @Column(name = "encerrada_por", length = 80)
    private String encerradaPor;

    @Column(name = "motivo_encerramento", length = 200)
    private String motivoEncerramento;
}
