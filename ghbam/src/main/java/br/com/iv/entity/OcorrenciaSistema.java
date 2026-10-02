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
@Table(name = "ocorrencia_sistema")
public class OcorrenciaSistema extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idocorrenciasistema")
    private Integer idocorrenciasistema;

    @Column(length = 30)
    private String unidade;

    @Column(length = 40)
    private String tipo;

    @Column(length = 20)
    private String gravidade;

    @Column(length = 20)
    private String status;

    @Column(length = 80)
    private String modulo;

    @Column(length = 120)
    private String responsavel;

    @Column(length = 160)
    private String paciente;

    @Column(length = 40)
    private String bam;

    @Column(length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 40)
    private String origem;

    @Column(length = 250)
    private String path;

    @Column(name = "criado_por", length = 120)
    private String criadoPor;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(columnDefinition = "TEXT")
    private String historico;
}
