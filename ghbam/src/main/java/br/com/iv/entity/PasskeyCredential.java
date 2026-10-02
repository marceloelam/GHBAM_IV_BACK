package br.com.iv.entity;

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
@Table(name = "passkey_credential")
public class PasskeyCredential extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 15, nullable = false)
    private String matricula;

    @Column(name = "nome_dispositivo", length = 120)
    private String nomeDispositivo;

    @Column(name = "credential_id", columnDefinition = "TEXT", nullable = false, unique = true)
    private String credentialId;

    @Column(name = "user_handle", columnDefinition = "TEXT", nullable = false)
    private String userHandle;

    @Column(name = "public_key_cose", columnDefinition = "TEXT", nullable = false)
    private String publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private Long signatureCount = 0L;

    @Column(nullable = false)
    private Boolean ativa = true;

    @Column(name = "discoverable", nullable = false)
    private Boolean discoverable = false;

    @Column(name = "backup_eligible", nullable = false)
    private Boolean backupEligible = false;

    @Column(name = "backed_up", nullable = false)
    private Boolean backedUp = false;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "ultimo_uso_em")
    private LocalDateTime ultimoUsoEm;
}
