package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "sessoes_ativas")
public class SessaoAtiva extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idsessao;

    @Column(length = 15, nullable = false)
    private String matricula;

    @Column(length = 55, nullable = false)
    private String nome;

    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 100)
    private String computador;

    @Column(length = 30)
    private String unidade;

    @Column(name = "data_login")
    private LocalDateTime dataLogin;

    @Column(name = "ultima_atividade")
    private LocalDateTime ultimaAtividade;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean ativa;
}
