package br.com.iv.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rag_documentos")
public class DocumentoEmbedding extends PanacheEntityBase {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idragdocumento;

    @Column(length = 500, nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String conteudo;

    @Column(length = 50, nullable = false)
    private String tipo;

    @Column(length = 50)
    private String setor;

    @Column(columnDefinition = "Integer default 0")
    private int idpaciente;

    @Column(columnDefinition = "Integer default 0")
    private int idprontuario;

    @Column(columnDefinition = "JSONB")
    private String metadados;

    @Column
    private LocalDateTime criadoem;

    @Column
    private LocalDateTime atualizadoem;

    @PrePersist
    protected void onCreate() {
        criadoem = LocalDateTime.now();
        atualizadoem = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoem = LocalDateTime.now();
    }
}
