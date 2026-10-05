package br.com.iv.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "pharmacia_condicao_unidade", uniqueConstraints = @UniqueConstraint(columnNames = {"unidade", "lote_id"}))
public class PharmaciaCondicaoUnidade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 20) public String unidade;
    @Column(name = "lote_id", nullable = false) public Long loteId;
    @Column(nullable = false) public boolean bloqueado;
    @Column(name = "validade_uso") public LocalDateTime validadeUso;
    @Column(length = 1000) public String motivo;
}
