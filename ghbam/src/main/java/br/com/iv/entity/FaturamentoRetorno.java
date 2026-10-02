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
@Table(name = "faturamento_retorno")
public class FaturamentoRetorno extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idretorno;

    private Integer idremessa;

    @Column(length = 6)
    private String competencia;

    @Column(length = 40)
    private String unidade;

    @Column(name = "data_importacao")
    private LocalDateTime dataImportacao;

    @Column(length = 40)
    private String usuario;

    @Column(name = "quantidade_registros")
    private int quantidadeRegistros;

    @Column(name = "quantidade_aprovados")
    private int quantidadeAprovados;

    @Column(name = "quantidade_rejeitados")
    private int quantidadeRejeitados;

    @Column(length = 500)
    private String observacao;
}
