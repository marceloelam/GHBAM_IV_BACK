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

/** Setor extra que pode pedir material (administracao, NIR, recepcao...). */
@Entity
@Table(name = "almox_setor")
public class AlmoxSetor extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idsetor")
    public Long idsetor;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "nome", nullable = false, length = 80)
    public String nome;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
    }
}
