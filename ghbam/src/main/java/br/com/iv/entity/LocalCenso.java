package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "LocalCenso")
public class LocalCenso extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idsetorlocalcenso;

    @Column(length = 15)
    private String codigo;

    @Column(length = 30)
    private String local;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean disponivel;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean excluido;

    @Column(length = 35)
    private String unidade;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "setorcenso_idsetorcenso", nullable = false)
    private SetorCenso setorCenso;

    public LocalCenso() {
        super();
        local = "";
        codigo = "";
        disponivel = true;
        excluido = false;
        unidade = "";
    }
}
