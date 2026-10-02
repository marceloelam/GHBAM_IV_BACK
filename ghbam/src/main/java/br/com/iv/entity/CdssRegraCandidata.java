package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "cdss_regra_candidata")
public class CdssRegraCandidata extends PanacheEntityBase implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "principio_ativo", nullable = false) public String principioAtivo;
    @Column(nullable = false) public String via;
    @Column(name = "regra_json", columnDefinition = "TEXT", nullable = false) public String regraJson;
    @Column(columnDefinition = "TEXT", nullable = false) public String fonte;
    @Column(nullable = false) public String status = "PENDENTE_DE_VALIDACAO";
    @Column(name = "criado_em", nullable = false) public java.time.Instant criadoEm = java.time.Instant.now();
    @Column(name = "validado_por") public String validadoPor;
    @Column(name = "validado_em") public java.time.Instant validadoEm;
}
