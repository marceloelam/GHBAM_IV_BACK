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
@Table(name = "fhir_envio_destino")
public class FhirEnvioDestino extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idfhirenviodestino;

    @Column(length = 25, nullable = false)
    private String unidade;

    @Column(length = 40, nullable = false)
    private String finalidade;

    @Column(length = 20, nullable = false)
    private String ambiente;

    @Column(name = "endpoint_base", length = 300)
    private String endpointBase;

    @Column(name = "tipo_autenticacao", length = 20, nullable = false)
    private String tipoAutenticacao;

    @Column(name = "credencial_criptografada", columnDefinition = "TEXT")
    private String credencialCriptografada;

    @Column(name = "api_key_header_nome", length = 80)
    private String apiKeyHeaderNome;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean ativo;

    @Column(name = "criado_por", length = 80)
    private String criadoPor;

    @Column(name = "atualizado_por", length = 80)
    private String atualizadoPor;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
