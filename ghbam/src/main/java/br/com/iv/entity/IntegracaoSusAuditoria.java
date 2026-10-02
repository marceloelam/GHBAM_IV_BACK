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
@Table(name = "integracao_sus_auditoria")
public class IntegracaoSusAuditoria extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idintegracaosusauditoria;

    @Column(length = 25, nullable = false)
    private String unidade;

    @Column(length = 20, nullable = false)
    private String ambiente;

    @Column(length = 60, nullable = false)
    private String acao;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(length = 80)
    private String usuario;

    @Column(length = 80)
    private String matricula;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    private LocalDateTime criadoEm;
}
