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
@Table(name = "reset_senha_solicitacao")
public class ResetSenhaSolicitacao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 15, nullable = false)
    private String matricula;

    @Column(name = "ip_solicitante", length = 45)
    private String ipSolicitante;

    @Column(length = 100)
    private String computador;

    @Column(name = "codigo_confirmacao", length = 4, nullable = false)
    private String codigoConfirmacao;

    @Column(length = 20, nullable = false)
    private String status; // PENDENTE, APROVADO, NEGADO, EXPIRADO, CONCLUIDO

    @Column(name = "aprovar_token", length = 100, unique = true)
    private String aprovarToken;

    @Column(name = "consulta_token", length = 100, unique = true)
    private String consultaToken;

    @Column(name = "token_confirmacao", length = 100, unique = true)
    private String tokenConfirmacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;
}
