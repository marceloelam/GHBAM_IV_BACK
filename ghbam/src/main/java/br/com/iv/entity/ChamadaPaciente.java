package br.com.iv.entity;

import java.io.Serializable;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity(name = "ChamadaPaciente")
public class ChamadaPaciente  extends PanacheEntityBase  implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
    private int idchamada;

    @Column(length = 70, nullable = false)
    private String nome;

    @Column(length = 50, nullable = false)
    private String local;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamou;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamou2;
    private String data;
    private String hora;

    @Column(length = 20)
    private String setor;

    @Column(length = 35)
    private String unidade;

    @Column(length = 20)
    private String pacienteespecial;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p1;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p2;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p3;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p4;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p5;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p6;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p7;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p8;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p9;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean p10;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup1;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup2;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup3;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup4;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup5;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup6;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup7;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup8;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup9;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean chamoup10;

    public ChamadaPaciente() {
        iniciarvariavel();
    }

    public void iniciarvariavel() {
        nome = "";
        local = "";
        setor = "";
        unidade = "";
        pacienteespecial = "";
        p1 = false;
        p2 = false;
        p3 = false;
        p4 = false;
        p5 = false;
        p6 = false;
        p7 = false;
        p8 = false;
        p9 = false;
        p10 = false;

        chamoup1 = false;
        chamoup2 = false;
        chamoup3 = false;
        chamoup4 = false;
        chamoup5 = false;
        chamoup6 = false;
        chamoup7 = false;
        chamoup8 = false;
        chamoup9 = false;
        chamoup10 = false;
    }
}