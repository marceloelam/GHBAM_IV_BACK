package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_estoque", uniqueConstraints = @UniqueConstraint(columnNames = {"lote_id", "unidade_sigla"}))
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaEstoque extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "unidade"})
    private PharmaciaLote lote;

    @Column(name = "unidade_sigla", length = 20)
    private String unidadeSigla;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(length = 50)
    private String localizacao;

    // Registro de proveniencia, ex: "Recebido via permuta #12 da unidade
    // HMSFX em 20/07/2026 14:32 por Joao Silva (mat. 12345)." — preenchido
    // automaticamente ao confirmar recebimento de uma permuta.
    @Column(length = 500)
    private String observacao;

    @Column(nullable = false)
    private Integer estoqueMinimo = 0;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PharmaciaLote getLote() { return lote; }
    public void setLote(PharmaciaLote lote) { this.lote = lote; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public String getLocalizacao() { return localizacao; }
    public void setLocalizacao(String localizacao) { this.localizacao = localizacao; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public Integer getEstoqueMinimo() { return estoqueMinimo; }
    public void setEstoqueMinimo(Integer estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
