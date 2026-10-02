package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "bulario")
public class Bulario  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	  @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int id;

    @Column(nullable = false)
    public String nome;

    @Column(name = "principio_ativo")
    public String principioAtivo;

    @Column(columnDefinition = "TEXT")
    public String resumo;

    @Column(columnDefinition = "TEXT")
    public String indicacoes;

    @Column(columnDefinition = "TEXT")
    public String posologia;

    @Column(columnDefinition = "TEXT")
    public String contraindicacoes;

    @Column(name = "efeito")
    public String efeitosAdversos;

    @Column(columnDefinition = "TEXT")
    public String interacoes;

    @Column(columnDefinition = "TEXT")
    public String precaucoes;

    @Column(columnDefinition = "TEXT")
    public String fonte;

    @Column(columnDefinition = "TEXT")
    public String tarja;

    @Column(name = "data_extracao")
    public String dataExtracao;


	

}
