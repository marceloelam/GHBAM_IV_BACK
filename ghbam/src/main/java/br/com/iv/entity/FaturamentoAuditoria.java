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
@Table(name = "faturamento_auditoria")
public class FaturamentoAuditoria extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idfaturamentoauditoria;

    @Column(length = 30)
    private String unidade;

    @Column(length = 60, nullable = false)
    private String acao;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(length = 60)
    private String entidade;

    @Column(length = 40)
    private String identidade;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_novo", columnDefinition = "TEXT")
    private String valorNovo;

    @Column(length = 500)
    private String motivo;

    @Column(length = 80)
    private String usuario;

    @Column(length = 40)
    private String matricula;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}
