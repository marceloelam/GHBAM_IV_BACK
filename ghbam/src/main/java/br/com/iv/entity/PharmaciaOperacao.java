package br.com.iv.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Registro imutavel de operacoes da farmacia da unidade, independente da CAF. */
@Entity
@Table(name = "pharmacia_operacao_unidade", uniqueConstraints = @UniqueConstraint(columnNames = {"unidade", "chave"}))
public class PharmaciaOperacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 20) public String unidade;
    @Column(name = "lote_id", nullable = false) public Long loteId;
    @Column(nullable = false, length = 30) public String tipo;
    @Column(nullable = false, length = 30) public String status;
    @Column(nullable = false, length = 40) public String matricula;
    @Column(name = "avaliador", length = 40) public String avaliador;
    @Column(name = "criado_em", nullable = false) public LocalDateTime criadoEm;
    @Column(name = "avaliado_em") public LocalDateTime avaliadoEm;
    @Column(nullable = false, length = 1000) public String justificativa;
    @Column(name = "parecer", length = 1000) public String parecer;
    @JsonIgnore @Column(length = 80) public String chave;
    @JsonIgnore @Column(length = 64) public String hash;
    public Integer quantidade;
    @Column(name = "lote_destino_id") public Long loteDestinoId;
    @JsonIgnore @Column(name = "saldo_referencia") public Integer saldoReferencia;
    @Column(name = "origem_id") public Long origemId;
    @Column(name = "dispensacao_id") public Long dispensacaoId;
    @Column(precision = 8, scale = 3) public BigDecimal temperatura;
    @Column(name = "limite_minimo", precision = 8, scale = 3) public BigDecimal limiteMinimo;
    @Column(name = "limite_maximo", precision = 8, scale = 3) public BigDecimal limiteMaximo;
    @Column(name = "validade_uso") public LocalDateTime validadeUso;
    @Column(length = 120) public String documento;
    @Column(name = "documento_nome", length = 120) public String documentoNome;
}
