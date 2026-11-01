package br.com.iv.ponto;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rh_ponto_marcacao")
public class PontoMarcacao extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmarcacao")
    public Long idmarcacao;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "matricula", nullable = false, length = 40)
    public String matricula;

    @Column(name = "nome", length = 160)
    public String nome;

    @Column(name = "tipo", nullable = false, length = 10)
    public String tipo;

    @Column(name = "datahora", nullable = false)
    public LocalDateTime dataHora;

    @Column(name = "origem", nullable = false, length = 10)
    public String origem;

    @Column(name = "similaridade", precision = 5, scale = 4)
    public BigDecimal similaridade;

    @Column(name = "terminal", length = 120)
    public String terminal;

    @Column(name = "justificativa", columnDefinition = "text")
    public String justificativa;

    @Column(name = "usuarioajuste", length = 80)
    public String usuarioAjuste;

    @Column(name = "anulada", nullable = false)
    public Boolean anulada;

    @Column(name = "motivoanulacao", columnDefinition = "text")
    public String motivoAnulacao;

    @Column(name = "usuarioanulacao", length = 80)
    public String usuarioAnulacao;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
        if (anulada == null) anulada = Boolean.FALSE;
    }
}
