package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_categoria", uniqueConstraints = @UniqueConstraint(columnNames = {"nome", "unidade_sigla"}))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PharmaciaCategoria extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 255)
    private String descricao;

    @Column(name = "unidade_sigla", length = 20)
    private String unidadeSigla;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }
}
