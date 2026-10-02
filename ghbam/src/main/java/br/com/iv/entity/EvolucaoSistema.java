package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "evolucao_sistema")
public class EvolucaoSistema extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idevolucaosistema")
    private Integer idevolucaosistema;

    @Column(length = 30)
    private String unidade;

    @Column(length = 80)
    private String modulo;

    @Column(length = 40)
    private String tipo;

    @Column(length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(columnDefinition = "TEXT")
    private String impacto;

    @Column(columnDefinition = "TEXT")
    private String arquivos;

    @Column(length = 40)
    private String origem;

    @Column(length = 120)
    private String autor;

    @Column(length = 40)
    private String matricula;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}
