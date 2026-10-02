package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_lote", uniqueConstraints = @UniqueConstraint(columnNames = {"produto_id", "numeroLote"}))
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaLote extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String numeroLote;

    @Column(nullable = false)
    private LocalDate dataFabricacao;

    @Column(nullable = false)
    private LocalDate dataValidade;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private PharmaciaProduto produto;

    @Column(nullable = false)
    private Integer quantidadeInicial;

    @Column(nullable = false)
    private Integer quantidadeAtual;

    @Column(nullable = false)
    private Boolean bloqueado = false;

    @Column(name = "unidade_sigla", length = 20)
    private String unidadeSigla;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroLote() { return numeroLote; }
    public void setNumeroLote(String numeroLote) { this.numeroLote = numeroLote; }

    public LocalDate getDataFabricacao() { return dataFabricacao; }
    public void setDataFabricacao(LocalDate dataFabricacao) { this.dataFabricacao = dataFabricacao; }

    public LocalDate getDataValidade() { return dataValidade; }
    public void setDataValidade(LocalDate dataValidade) { this.dataValidade = dataValidade; }

    public PharmaciaProduto getProduto() { return produto; }
    public void setProduto(PharmaciaProduto produto) { this.produto = produto; }

    public Integer getQuantidadeInicial() { return quantidadeInicial; }
    public void setQuantidadeInicial(Integer quantidadeInicial) { this.quantidadeInicial = quantidadeInicial; }

    public Integer getQuantidadeAtual() { return quantidadeAtual; }
    public void setQuantidadeAtual(Integer quantidadeAtual) { this.quantidadeAtual = quantidadeAtual; }

    public Boolean getBloqueado() { return bloqueado; }
    public void setBloqueado(Boolean bloqueado) { this.bloqueado = bloqueado; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }
}
