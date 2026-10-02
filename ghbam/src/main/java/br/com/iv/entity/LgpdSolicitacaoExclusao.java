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
@Table(name = "lgpd_solicitacao_exclusao")
public class LgpdSolicitacaoExclusao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idsolicitacao;

    @Column(nullable = false)
    private Integer idpaciente;

    @Column(name = "nome_solicitante_externo", length = 150)
    private String nomeSolicitanteExterno;

    @Column(name = "detalhe_pedido", columnDefinition = "TEXT")
    private String detalhePedido;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "base_legal", columnDefinition = "TEXT", nullable = false)
    private String baseLegal;

    @Column(name = "matricula_atendente", length = 20, nullable = false)
    private String matriculaAtendente;

    @Column(name = "nome_atendente", length = 150)
    private String nomeAtendente;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
