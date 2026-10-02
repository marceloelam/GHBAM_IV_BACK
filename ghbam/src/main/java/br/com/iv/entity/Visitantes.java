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
@Table(name = "visitantes")
public class Visitantes  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idvisitante;
	@Column(length = 55)
	private String nomeVisitante;
	@Column(length = 18)
	private String cpfVisitante;
	@Column(length = 18)
	private String identidadeVisitante;
	@Column(length = 12)
	private String dataVisita;
	@Column(length = 5)
	private String horaVisitante;
	@Column(length = 12)
	private String nascimentoVisitante;
	@Column(length = 20)
	private String nbam;
	@Column(length = 600)
	private String obsvisitante;
	@Column(length = 100)
	private String endereco;
	@Column(length = 30)
	private String telefone;
	@Column(columnDefinition = "TEXT")
	private String fotovisitante;
	@Column(length = 100)
	private String fotovisitantearquivo;
	@Column(length = 30)
	private String nunidade;



}
