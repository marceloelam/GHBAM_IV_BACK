package br.com.iv.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rag_aprendizado")
public class RagAprendizado extends PanacheEntityBase {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idaprendizado;

    @Column(length = 500, nullable = false)
    private String pergunta;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String resposta;

    @Column(columnDefinition = "TEXT")
    private String contextoResumido;

    @Column(columnDefinition = "boolean default false")
    private boolean usouWeb;

    @Column(columnDefinition = "Integer default 0")
    private int idpaciente;

    @Column(columnDefinition = "Integer default 0")
    private int feedback;

    @Column(columnDefinition = "Integer default 1")
    private int usos;

    @Column
    private LocalDateTime dataCriacao;

    @Column
    private LocalDateTime ultimoUso;

    @Column
    private String embedPergunta;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        ultimoUso = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        ultimoUso = LocalDateTime.now();
    }
}
