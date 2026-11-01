package br.com.iv.cme;

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
@Table(name = "cme_evento")
public class CmeEvento extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idevento")
    public Long idevento;

    @Column(name = "idciclo", nullable = false)
    public Long idciclo;

    @Column(name = "idpacote")
    public Long idpacote;

    @Column(name = "evento", nullable = false, length = 40)
    public String evento;

    @Column(name = "statusanterior", length = 20)
    public String statusAnterior;

    @Column(name = "statusnovo", length = 20)
    public String statusNovo;

    @Column(name = "detalhe", columnDefinition = "text")
    public String detalhe;

    @Column(name = "usuario", length = 80)
    public String usuario;

    @Column(name = "dataevento")
    public LocalDateTime dataEvento;

    @PrePersist
    public void prePersist() {
        if (dataEvento == null) {
            dataEvento = LocalDateTime.now();
        }
    }
}
