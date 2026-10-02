package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "pep_anotacao_rascunho")
public class PepAnotacaoRascunho extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idbam")
    private Integer idbam;

    @Column(name = "unidade", length = 120)
    private String unidade;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "criado_por_matricula", length = 40)
    private String criadoPorMatricula;

    @Column(name = "criado_por_nome", length = 160)
    private String criadoPorNome;

    @Column(name = "criado_por_cargo", length = 120)
    private String criadoPorCargo;

    @Column(name = "criado_por_conselho", length = 80)
    private String criadoPorConselho;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizado_por_matricula", length = 40)
    private String atualizadoPorMatricula;

    @PrePersist
    protected void aoPersistir() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    protected void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }
}
