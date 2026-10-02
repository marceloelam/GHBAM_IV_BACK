package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_permuta_item")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaPermutaItem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permuta_id", nullable = false)
    private PharmaciaPermuta permuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private PharmaciaLote lote;

    @Column(nullable = false)
    private Integer quantidadeSolicitada;

    @Column(nullable = false)
    private Integer quantidadeAprovada;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PharmaciaPermuta getPermuta() { return permuta; }
    public void setPermuta(PharmaciaPermuta permuta) { this.permuta = permuta; }

    public PharmaciaLote getLote() { return lote; }
    public void setLote(PharmaciaLote lote) { this.lote = lote; }

    public Integer getQuantidadeSolicitada() { return quantidadeSolicitada; }
    public void setQuantidadeSolicitada(Integer quantidadeSolicitada) { this.quantidadeSolicitada = quantidadeSolicitada; }

    public Integer getQuantidadeAprovada() { return quantidadeAprovada; }
    public void setQuantidadeAprovada(Integer quantidadeAprovada) { this.quantidadeAprovada = quantidadeAprovada; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
