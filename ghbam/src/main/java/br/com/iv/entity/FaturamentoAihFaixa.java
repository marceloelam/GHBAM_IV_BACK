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

/**
 * Faixa de numeracao de AIH autorizada pelo gestor.
 *
 * O SISAIH01 exige o NU_AIH (13 digitos) e recusa numero fora da faixa concedida ou
 * repetido. O hospital nao inventa esse numero: recebe um bloco do gestor municipal ou
 * estadual e o consome. Guardar a faixa aqui e o que permite ao GHBAM gerar o arquivo da
 * AIH a partir da propria producao, em vez de depender de digitacao.
 *
 * O proximoNumero mora na faixa, e nao e deduzido das contas ja numeradas, porque pular
 * ou devolver numero e decisao do faturamento - deve ser alteracao explicita e auditavel,
 * nao efeito de recontagem.
 */
@Data
@Entity
@Table(name = "faturamento_aih_faixa")
public class FaturamentoAihFaixa extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idfaixa;

    @Column(length = 30, nullable = false)
    private String unidade;

    /** AAAAMM. Nulo quando a faixa vale para qualquer competencia. */
    @Column(length = 6)
    private String competencia;

    @Column(name = "numero_inicial", length = 13, nullable = false)
    private String numeroInicial;

    @Column(name = "numero_final", length = 13, nullable = false)
    private String numeroFinal;

    @Column(name = "proximo_numero", length = 13, nullable = false)
    private String proximoNumero;

    @Column(name = "orgao_emissor", length = 10)
    private String orgaoEmissor;

    @Column(length = 255)
    private String observacao;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "usuario_criador", length = 60)
    private String usuarioCriador;

    /** Quantos numeros a faixa ainda tem. Zero quando esgotada. */
    public long getDisponiveis() {
        try {
            long fim = Long.parseLong(numeroFinal.trim());
            long proximo = Long.parseLong(proximoNumero.trim());
            return Math.max(0, fim - proximo + 1);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
