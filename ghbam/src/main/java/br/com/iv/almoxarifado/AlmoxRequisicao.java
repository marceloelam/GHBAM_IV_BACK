package br.com.iv.almoxarifado;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "almox_requisicao")
public class AlmoxRequisicao extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idrequisicao")
    public Long idrequisicao;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "setor", nullable = false, length = 80)
    public String setor;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "observacao", columnDefinition = "text")
    public String observacao;

    @Column(name = "matriculasolicitante", nullable = false, length = 80)
    public String matriculaSolicitante;

    @Column(name = "nomesolicitante", length = 160)
    public String nomeSolicitante;

    @Column(name = "datasolicitacao")
    public LocalDateTime dataSolicitacao;

    @Column(name = "matriculaatendente", length = 80)
    public String matriculaAtendente;

    @Column(name = "dataatendimento")
    public LocalDateTime dataAtendimento;

    @Column(name = "motivo", columnDefinition = "text")
    public String motivo;

    @PrePersist
    public void prePersist() {
        if (dataSolicitacao == null) dataSolicitacao = LocalDateTime.now();
    }
}
