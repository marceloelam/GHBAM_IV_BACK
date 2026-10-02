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
@Table(name = "ocorrencia_alerta_config")
public class OcorrenciaAlertaConfig extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idocorrenciaalertaconfig")
    private Integer idocorrenciaalertaconfig;

    @Column(length = 30, unique = true)
    private String unidade;

    @Column(length = 1)
    private String ativo;

    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
