package br.com.iv.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "pedido")
public class Pedido implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String unidade;
    
    @Column(nullable = false)
    private String origem;
    
    @Column
    private String unidadeSigla;

    @Column(columnDefinition = "TEXT DEFAULT ''")
    private String comentario;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PedidoItem> itens = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate criadoEm;

    public Pedido() { }

    public Pedido(String origem, String unidade, String unidadeSigla, String comentario) {
    	this.origem = origem;
        this.unidade = unidade;
        this.unidadeSigla = unidadeSigla;
        this.comentario = comentario;
    }

    @PrePersist
    protected void onCreate() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public List<PedidoItem> getItens() {
        return Collections.unmodifiableList(itens);
    }
    public void setItens(List<PedidoItem> itens) {
        this.itens.clear();
        if (itens != null) {
            itens.forEach(this::addItem);
        }
    }

    public void addItem(PedidoItem item) {
        if (item == null) return;
        item.setPedido(this);
        this.itens.add(item);
    }

    public void removeItem(PedidoItem item) {
        if (item == null) return;
        item.setPedido(null);
        this.itens.remove(item);
    }

    public LocalDate getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDate criadoEm) { this.criadoEm = criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido)) return false;
        Pedido pedido = (Pedido) o;
        return id != null && Objects.equals(id, pedido.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}