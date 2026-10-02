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
@Table(name = "s_pasrv")
public class ProcedimentosPasrv extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idpasrv;
	@Column(length = 6)
	private String pasrv_cmp;
	@Column(length = 9)
	private String pasrv_pa;
	@Column(length = 3)
	private String pasrv_srv;
	@Column(length = 3)
	private String pasrv_csf;
	@Column(length = 6)
	private String pasrvchksm;


}
