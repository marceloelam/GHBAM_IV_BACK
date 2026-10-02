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
@Table(name = "push_dispositivo")
public class PushDispositivo extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 15, nullable = false)
    private String matricula;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String endpoint;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String p256dh;

    @Column(name = "auth_key", columnDefinition = "TEXT", nullable = false)
    private String authKey;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ultimo_uso")
    private LocalDateTime ultimoUso;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "nome_dispositivo", length = 120)
    private String nomeDispositivo;
}
