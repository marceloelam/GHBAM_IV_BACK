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
@Table(name = "faturamento_remessa")
public class FaturamentoRemessa extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idremessa;

    // 60 porque o numero carrega a sigla da unidade
    // ('GHBAM-202607-<UNIDADE>-00001'): com 30 a geracao quebrava no insert em unidade
    // de nome longo. Ver V118__amplia_numero_layout_faturamento_remessa.sql.
    @Column(length = 60, nullable = false, unique = true)
    private String numero;

    @Column(nullable = false)
    private int idcompetencia;

    @Column(length = 40)
    private String unidade;

    @Column(length = 20, nullable = false)
    private String tipo;

    // O layout oficial do BPA e 'BPA-DATASUS-FEHOSP-2020', 23 caracteres: com 20 o
    // insert da remessa estourava a coluna.
    @Column(length = 40)
    private String layout;

    @Column(length = 20, nullable = false)
    private String status;

    private int quantidade;

    @Column(name = "valor_estimado")
    private double valorEstimado;

    @Column(length = 64)
    private String hash;

    private Integer tamanho;

    @Column(name = "caminho_arquivo", length = 300)
    private String caminhoArquivo;

    @Column(name = "remessa_original_id")
    private Integer remessaOriginalId;

    @Column(name = "data_geracao")
    private LocalDateTime dataGeracao;

    @Column(name = "usuario_geracao", length = 40)
    private String usuarioGeracao;

    @Column(name = "data_transmissao")
    private LocalDateTime dataTransmissao;

    @Column(name = "usuario_transmissao", length = 40)
    private String usuarioTransmissao;

    @Column(name = "protocolo_transmissao", length = 80)
    private String protocoloTransmissao;

    @Column(name = "observacao_transmissao", length = 500)
    private String observacaoTransmissao;
}
