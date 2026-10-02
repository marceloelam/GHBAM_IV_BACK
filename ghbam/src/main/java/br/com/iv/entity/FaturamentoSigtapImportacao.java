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
@Table(name = "faturamento_sigtap_importacao")
public class FaturamentoSigtapImportacao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idsigtapimportacao;

    @Column(length = 6, nullable = false)
    private String competencia;

    @Column(length = 40)
    private String unidade;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "quantidade_procedimentos")
    private int quantidadeProcedimentos;

    @Column(name = "quantidade_erros")
    private int quantidadeErros;

    @Column(name = "data_importacao")
    private LocalDateTime dataImportacao;

    @Column(length = 40)
    private String usuario;

    @Column(length = 500)
    private String observacao;
}
