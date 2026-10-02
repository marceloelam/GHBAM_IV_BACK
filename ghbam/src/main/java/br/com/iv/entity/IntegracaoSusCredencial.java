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
@Table(name = "integracao_sus_credencial")
public class IntegracaoSusCredencial extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idintegracaosuscredencial;

    @Column(length = 25, nullable = false)
    private String unidade;

    @Column(length = 20, nullable = false)
    private String ambiente;

    @Column(length = 120)
    private String orgaoGestor;

    @Column(length = 120)
    private String usuarioTecnico;

    @Column(length = 160)
    private String clientId;

    @Column(columnDefinition = "TEXT")
    private String clientSecretCriptografado;

    @Column(length = 160)
    private String certificadoAlias;

    @Column(columnDefinition = "TEXT")
    private String certificadoPfxCriptografado;

    @Column(columnDefinition = "TEXT")
    private String certificadoSenhaCriptografada;

    @Column(length = 300)
    private String endpointBase;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean ativo;

    @Column(length = 80)
    private String criadoPor;

    @Column(length = 80)
    private String atualizadoPor;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
