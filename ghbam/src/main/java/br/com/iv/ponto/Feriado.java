package br.com.iv.ponto;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Feriado estadual/municipal ou ponto facultativo (os nacionais sao calculados em PontoApuracao). */
@Entity
@Table(name = "rh_feriado")
public class Feriado extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idferiado")
    public Long idferiado;

    @Column(name = "data", nullable = false)
    public LocalDate data;

    @Column(name = "descricao", nullable = false, length = 120)
    public String descricao;

    @Column(name = "tipo", nullable = false, length = 20)
    public String tipo;

    /** Nula = vale para todas as unidades. */
    @Column(name = "unidade", length = 20)
    public String unidade;

    @Column(name = "usuario", length = 80)
    public String usuario;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
        if (tipo == null) tipo = "MUNICIPAL";
    }
}
