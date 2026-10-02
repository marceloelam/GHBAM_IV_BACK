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
@Table(name = "configuracao_conformidade_sla_historico")
public class ConfiguracaoConformidadeSlaHistorico extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idconfiguracaoconformidadeslahistorico")
    private Integer idconfiguracaoconformidadeslahistorico;

    @Column(name = "idconfiguracaoconformidadesla")
    private Integer idconfiguracaoconformidadesla;

    @Column(length = 30)
    private String unidade;

    @Column(length = 50)
    private String categoria;

    @Column(length = 30)
    private String acao;

    @Column(length = 120)
    private String usuario;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_novo", columnDefinition = "TEXT")
    private String valorNovo;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}
