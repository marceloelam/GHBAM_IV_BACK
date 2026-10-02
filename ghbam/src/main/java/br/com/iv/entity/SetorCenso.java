package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "SetorCenso")
public class SetorCenso extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idsetorcenso;

    @Column(length = 15)
    private String codigo;

    @Column(length = 30)
    private String setor;

    @Column(length = 35)
    private String unidade;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean excluido;

    public SetorCenso() {
        super();
        iniciarVariavel();
    }

    private void iniciarVariavel() {
        setor = "";
        codigo = "";
        excluido = false;
        unidade = "";
    }

    public void limparServ() {
        iniciarVariavel();
    }
}
