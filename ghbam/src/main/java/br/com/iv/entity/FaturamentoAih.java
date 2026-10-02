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
@Table(name = "faturamento_aih")
public class FaturamentoAih extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idaih;

    @Column(length = 30, nullable = false, unique = true)
    private String numero;

    /**
     * Numero oficial da AIH (NU_AIH, 13 digitos), tirado da faixa concedida pelo gestor.
     * Fica separado de `numero`, que e a identificacao interna (AIH-202607-00082) usada
     * para achar a conta na tela. Nulo enquanto nao houver faixa cadastrada.
     */
    @Column(name = "numero_aih", length = 13)
    private String numeroAih;

    @Column(name = "idinternacao", nullable = false)
    private int idInternacao;

    private int idpaciente;

    @Column(length = 20)
    private String prontuario;

    @Column(length = 20)
    private String bam;

    @Column(length = 25)
    private String unidade;

    @Column(length = 15)
    private String cnes;

    @Column(name = "procedimento_principal", length = 10)
    private String procedimentoPrincipal;

    @Column(length = 20)
    private String cid;

    @Column(name = "carater_atendimento", length = 20)
    private String caraterAtendimento;

    private Integer idcompetencia;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(name = "valor_estimado")
    private double valorEstimado;

    @Column(name = "data_internacao")
    private LocalDateTime dataInternacao;

    @Column(name = "data_alta")
    private LocalDateTime dataAlta;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "usuario_criador", length = 40)
    private String usuarioCriador;

    @Transient
    private long pendenciasClassificacao;

    // Producao que nao entra na conta: nao faturavel (valor zero) e a que ficou de fora
    // por critica. Transiente porque e leitura do estado atual da producao, nao dado da
    // AIH - gravar congelaria um numero que muda a cada correcao em /producao.
    @Transient
    private long linhasForaDaConta;

    // Quantas linhas realmente compoem a conta. Sem este numero a lista nao tinha como
    // distinguir "conta com pendencias, mas fechavel" de "conta vazia, que o servidor vai
    // recusar" - as duas mostravam so o que ficou de fora, e a tela prometia um
    // fechamento impossivel.
    @Transient
    private long linhasAptas;

    @Transient
    private java.util.List<String> motivosForaDaConta = new java.util.ArrayList<>();
}
