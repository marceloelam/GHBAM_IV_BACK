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
@Table(name = "rxprocedimentos")
public class ProcedimentosRX  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idrx;
	@Column(length = 15)
	private String codprocedimento;
	@Column(length = 4)
	private String cid;
	@Column(length = 120)
	private String descricao;
	@Column(length = 30)
	private String modo;
	@Column(length = 15)
	private String modo2;
	
	

}
