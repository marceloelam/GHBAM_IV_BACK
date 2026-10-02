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
@Entity(name = "receitamedicamentos")
public class ReceitaMedicamentos  extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idreceitamedicamento;

    private int nbam;
    private String remedio;
    private String complemento;
    private String qtde;
    private String tipo;
    private String tempo;
    private String durante;
    private String periodo;
    private String observacao;
    private String medicocrm;
    private String dataehora;

    public ReceitaMedicamentos() {
        super();
        this.nbam = 0;
        this.remedio = "";
        this.complemento = "";
        this.qtde = "01";
        this.tipo = "comp";
        this.tempo = "01 vez";
        this.durante = "ao dia(s)";
        this.periodo = "dose única";
        this.observacao = "";
        this.medicocrm = "";
        this.dataehora = "";
    }
}
