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
@Table(name = "almox_patrimonio_evento")
public class AlmoxPatrimonioEvento extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idevento")
    public Long idevento;

    @Column(name = "idpatrimonio", nullable = false)
    public Long idpatrimonio;

    @Column(name = "tipo", nullable = false, length = 20)
    public String tipo;

    @Column(name = "setororigem", length = 80)
    public String setorOrigem;

    @Column(name = "setordestino", length = 80)
    public String setorDestino;

    @Column(name = "detalhe", columnDefinition = "text")
    public String detalhe;

    @Column(name = "usuario", length = 80)
    public String usuario;

    @Column(name = "dataevento")
    public LocalDateTime dataEvento;

    @PrePersist
    public void prePersist() {
        if (dataEvento == null) dataEvento = LocalDateTime.now();
    }
}
