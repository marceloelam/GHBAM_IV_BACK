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
import jakarta.persistence.Transient;
import lombok.Data;

@Data
@Entity
@Table(name = "faturamento_competencia")
public class FaturamentoCompetencia extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idcompetencia;

    @Column(length = 6, nullable = false)
    private String competencia;

    @Column(length = 40)
    private String unidade;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "versao_sigtap_utilizada", length = 6)
    private String versaoSigtapUtilizada;

    @Column(name = "quantidade_producao")
    private int quantidadeProducao;

    // Nao persistido: contagem ao vivo, calculada a cada listagem. quantidadeProducao
    // (acima) so e preenchido no fechamento (snapshot auditado), entao fica sempre 0
    // enquanto a competencia esta ABERTA mesmo com producao real ja vinculada - este
    // campo cobre a listagem/tela sem mexer no snapshot de fechamento.
    @Transient
    private int quantidadeProducaoAtual;

    @Transient
    private long quantidadeCriticasAbertas;

    @Transient
    private long pendentesSemVinculacao;

    @Column(name = "quantidade_criticas")
    private int quantidadeCriticas;

    @Column(name = "valor_estimado")
    private double valorEstimado;

    @Column(name = "data_abertura")
    private LocalDateTime dataAbertura;

    @Column(name = "usuario_abertura", length = 40)
    private String usuarioAbertura;

    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    @Column(name = "usuario_fechamento", length = 40)
    private String usuarioFechamento;

    @Column(name = "data_reabertura")
    private LocalDateTime dataReabertura;

    @Column(name = "usuario_reabertura", length = 40)
    private String usuarioReabertura;

    @Column(name = "motivo_reabertura", length = 500)
    private String motivoReabertura;
}
