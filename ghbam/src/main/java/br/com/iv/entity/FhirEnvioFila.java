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
@Table(name = "fhir_envio_fila")
public class FhirEnvioFila extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idfhirenviofila;

    @Column(name = "tipo_documento", length = 60, nullable = false)
    private String tipoDocumento;

    @Column(nullable = false)
    private Integer iddocumento;

    private Integer idbam;

    private Integer idpaciente;

    private Integer idassinatura;

    @Column(length = 25, nullable = false)
    private String unidade;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "payload_fhir", columnDefinition = "TEXT")
    private String payloadFhir;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(name = "resposta_http", columnDefinition = "TEXT")
    private String respostaHttp;

    // Referencia devolvida pelo repositorio (Location/Content-Location),
    // necessaria para eventual substituicao/retificacao homologada.
    @Column(name = "referencia_remota", length = 1000)
    private String referenciaRemota;

    @Column(nullable = false)
    private Integer tentativas;

    @Column(name = "proxima_tentativa_em")
    private LocalDateTime proximaTentativaEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;
}
