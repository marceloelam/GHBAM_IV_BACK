package br.com.iv.entity;

import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Incidente ou evento adverso notificado ao Nucleo de Seguranca do Paciente.
 * A notificacao e nao punitiva: quando anonimo = true os campos de notificante
 * ficam nulos de proposito e nunca sao preenchidos depois.
 */
@Data
@Entity
@Table(name = "nsp_evento")
public class NspEvento extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idevento")
    private Long idevento;

    @Column(nullable = false, length = 40)
    private String protocolo;

    @Column(nullable = false, length = 20)
    private String unidade;

    @Column(name = "tipo_incidente", nullable = false, length = 60)
    private String tipoIncidente;

    @Column(length = 40)
    private String classificacao;

    @Column(name = "grau_dano", length = 20)
    private String grauDano;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "data_ocorrencia")
    private LocalDateTime dataOcorrencia;

    @Column(name = "local_ocorrencia", length = 120)
    private String localOcorrencia;

    @Column(length = 20)
    private String turno;

    private Integer idpaciente;

    private Integer idbam;

    @Column(length = 40)
    private String prontuario;

    @Column(name = "nome_paciente", length = 160)
    private String nomePaciente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "acao_imediata", columnDefinition = "TEXT")
    private String acaoImediata;

    @Column(nullable = false)
    private Boolean anonimo;

    @Column(name = "notificante_nome", length = 160)
    private String notificanteNome;

    @Column(name = "notificante_matricula", length = 40)
    private String notificanteMatricula;

    @Column(name = "notificante_cargo", length = 120)
    private String notificanteCargo;

    @Column(name = "prazo_analise")
    private LocalDateTime prazoAnalise;

    @Column(name = "causa_raiz", columnDefinition = "TEXT")
    private String causaRaiz;

    @Column(length = 160)
    private String medicamento;

    @Column(name = "dose_administrada", length = 80)
    private String doseAdministrada;

    @Column(name = "via_administracao", length = 40)
    private String viaAdministracao;

    private Integer idprescricao;

    @Column(length = 20)
    private String causalidade;

    private Boolean evitavel;

    @Column(columnDefinition = "TEXT")
    private String conclusao;

    @Column(name = "encerrado_em")
    private LocalDateTime encerradoEm;

    @Column(name = "encerrado_por", length = 160)
    private String encerradoPor;

    @Column(name = "notivisa_notificado", nullable = false)
    private Boolean notivisaNotificado;

    @Column(name = "notivisa_protocolo", length = 60)
    private String notivisaProtocolo;

    @Column(name = "data_registro")
    private LocalDateTime dataRegistro;

    @Column(name = "usuario_registro", length = 80)
    private String usuarioRegistro;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @Column(name = "usuario_atualizacao", length = 80)
    private String usuarioAtualizacao;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) {
            dataRegistro = LocalDateTime.now();
        }
        if (dataAtualizacao == null) {
            dataAtualizacao = dataRegistro;
        }
        if (anonimo == null) {
            anonimo = Boolean.FALSE;
        }
        if (notivisaNotificado == null) {
            notivisaNotificado = Boolean.FALSE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}
