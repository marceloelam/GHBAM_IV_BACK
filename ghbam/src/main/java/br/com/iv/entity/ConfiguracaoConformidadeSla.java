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
@Table(name = "configuracao_conformidade_sla")
public class ConfiguracaoConformidadeSla extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idconfiguracaoconformidadesla")
    private Integer idconfiguracaoconformidadesla;

    @Column(length = 30)
    private String unidade;

    @Column(length = 50)
    private String categoria;

    @Column(length = 120)
    private String responsavel;

    @Column(name = "sla_minutos")
    private Integer slaMinutos;

    @Column(columnDefinition = "TEXT")
    private String orientacao;

    @Column(length = 1)
    private String ativo;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
