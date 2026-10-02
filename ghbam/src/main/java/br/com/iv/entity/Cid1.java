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
@Entity(name = "s_cid")
public class Cid1  extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = -6385946862381744162L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idCid1;
	@Column(length = 4, nullable = false)
	private String cd_cod;
	@Column(length = 50, nullable = false)
	private String cd_descr;
	@Column(length = 1)
	private String opc;
	@Column(length = 1)
	private String cat;
	@Column(length = 1)
	private String subcat;
	@Column(length = 1)
	private String restrsexo;
	@Column(length = 3)
	private String campos_rad;
	@Column(length = 1)
	private String estadio;
	@Column(length = 1)
	private String repete_rad;
	@Column(length = 6)
	private String cmp_ini;
	@Column(length = 6)
	private String cmp_fim;
	@Column(length = 6)
	private String cid_chksm;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((campos_rad == null) ? 0 : campos_rad.hashCode());
		result = prime * result + ((cat == null) ? 0 : cat.hashCode());
		result = prime * result + ((cd_cod == null) ? 0 : cd_cod.hashCode());
		result = prime * result + ((cd_descr == null) ? 0 : cd_descr.hashCode());
		result = prime * result + ((cid_chksm == null) ? 0 : cid_chksm.hashCode());
		result = prime * result + ((cmp_fim == null) ? 0 : cmp_fim.hashCode());
		result = prime * result + ((cmp_ini == null) ? 0 : cmp_ini.hashCode());
		result = prime * result + ((estadio == null) ? 0 : estadio.hashCode());
		result = prime * result + idCid1;
		result = prime * result + ((opc == null) ? 0 : opc.hashCode());
		result = prime * result + ((repete_rad == null) ? 0 : repete_rad.hashCode());
		result = prime * result + ((restrsexo == null) ? 0 : restrsexo.hashCode());
		result = prime * result + ((subcat == null) ? 0 : subcat.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Cid1 other = (Cid1) obj;
		if (campos_rad == null) {
			if (other.campos_rad != null)
				return false;
		} else if (!campos_rad.equals(other.campos_rad))
			return false;
		if (cat == null) {
			if (other.cat != null)
				return false;
		} else if (!cat.equals(other.cat))
			return false;
		if (cd_cod == null) {
			if (other.cd_cod != null)
				return false;
		} else if (!cd_cod.equals(other.cd_cod))
			return false;
		if (cd_descr == null) {
			if (other.cd_descr != null)
				return false;
		} else if (!cd_descr.equals(other.cd_descr))
			return false;
		if (cid_chksm == null) {
			if (other.cid_chksm != null)
				return false;
		} else if (!cid_chksm.equals(other.cid_chksm))
			return false;
		if (cmp_fim == null) {
			if (other.cmp_fim != null)
				return false;
		} else if (!cmp_fim.equals(other.cmp_fim))
			return false;
		if (cmp_ini == null) {
			if (other.cmp_ini != null)
				return false;
		} else if (!cmp_ini.equals(other.cmp_ini))
			return false;
		if (estadio == null) {
			if (other.estadio != null)
				return false;
		} else if (!estadio.equals(other.estadio))
			return false;
		if (idCid1 != other.idCid1)
			return false;
		if (opc == null) {
			if (other.opc != null)
				return false;
		} else if (!opc.equals(other.opc))
			return false;
		if (repete_rad == null) {
			if (other.repete_rad != null)
				return false;
		} else if (!repete_rad.equals(other.repete_rad))
			return false;
		if (restrsexo == null) {
			if (other.restrsexo != null)
				return false;
		} else if (!restrsexo.equals(other.restrsexo))
			return false;
		if (subcat == null) {
			if (other.subcat != null)
				return false;
		} else if (!subcat.equals(other.subcat))
			return false;
		return true;
	}

	public Cid1() {
		super();
		this.idCid1 = 0;
		this.cd_cod = "";
		this.cd_descr = "";
		this.opc = "";
		this.cat = "";
		this.subcat = "";
		this.restrsexo = "";
		this.campos_rad = "";
		this.estadio = "";
		this.repete_rad = "";
		this.cmp_ini = "";
		this.cmp_fim = "";
		this.cid_chksm = "";
	}

}
