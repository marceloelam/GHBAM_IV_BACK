package br.com.iv.entity;

import java.io.Serializable;
import java.util.Date;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

@Data
@Entity
@Table(name = "escala")
public class Escala extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idescala;
    
    private String titulo;
    
    @Temporal(TemporalType.DATE)
    private Date inicioagenda;
    
    @Temporal(TemporalType.DATE)
    private Date fimagenda;
    
    private String horaescala;
    
    @Column(length = 30)
    private String setor;
    
    private String horasaida;
    
    @ManyToOne
    @JoinColumn(nullable = false)
    private Servidor servidor;
    
    @ManyToOne
    @JoinColumn(nullable = false)
    private Servidor cadastrou;
    
    @Column(length = 30)
    private String unidade;
    
    @Column(length = 600)
    private String obs;
    
    @Column(length = 600)
    private String obs2;
    
    private boolean statusevento;

    public Escala() {
        super();
        iniciarvariavel();
    }

    public void iniciarvariavel() {
        horaescala = "08:00";
        horasaida = "17:00";
        titulo = "";
        obs = "";
        obs2 = "";
        statusevento = false;
    }

    public void limpar_serv() {
        iniciarvariavel();
    }
}