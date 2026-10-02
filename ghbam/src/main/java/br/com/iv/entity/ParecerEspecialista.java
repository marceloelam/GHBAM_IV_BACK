package br.com.iv.entity;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;



@Entity(name = "parecerespecialista")
public class ParecerEspecialista  extends PanacheEntityBase  implements Serializable {

	private static final long serialVersionUID = 1L; // = 5297832317139223731L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idparecerespecialista;
	@Column(columnDefinition = "TEXT")
	private String desc1;
	@Temporal(TemporalType.DATE)
	private Date data;
	@Temporal(TemporalType.DATE)
	private Date dataresp;
	@Column(columnDefinition = "TEXT")
	private String desc2;
	@Column(length = 35)
	private String prontuario;
	@Column(length = 55)
	private String medicorequisitante;
	@Column(length = 35)
	private String crm;
	@Column(length = 55)
	private String medicorecebedor;
	@Column(length = 35)
	private String crmrecebedor;
	@Column(length = 35)
	private String especialidadeorigem;
	@Column(length = 35)
	private String especialidadedestino;
	@Column(length = 25)
	private String unidade;
	private int nbam;

	public ParecerEspecialista() {
		super();
		desc1 = "";
		desc2 = "";
		medicorequisitante = "";
		crmrecebedor = "";
		especialidadeorigem = "";
		especialidadedestino = "";
		unidade = "";
		crm = "";
		nbam = 0;
		prontuario = "";
		try {
			Date data3 = new Date();
			SimpleDateFormat formatador = new SimpleDateFormat("dd/MM/yyyy");
			String dat1 = formatador.format(data3);
			Date dataFormatada = formatador.parse(dat1);
			data = dataFormatada;

		} catch (ParseException e) {
			//
			e.printStackTrace();
		}
	}

	public int getIdparecerespecialista() {
		return idparecerespecialista;
	}

	public void setIdparecerespecialista(int idparecerespecialista) {
		this.idparecerespecialista = idparecerespecialista;
	}

	public String getDesc1() {
		return desc1;
	}

	public void setDesc1(String desc1) {
		this.desc1 = desc1;
	}

	public Date getData() {
		return data;
	}

	public void setData(Date data) {
		this.data = data;
	}

	public String getDesc2() {
		return desc2;
	}

	public void setDesc2(String desc2) {
		this.desc2 = desc2;
	}

	public String getProntuario() {
		return prontuario;
	}

	public void setProntuario(String prontuario) {
		this.prontuario = prontuario;
	}

	public String getMedicorequisitante() {
		return medicorequisitante;
	}

	public void setMedicorequisitante(String medicorequisitante) {
		this.medicorequisitante = medicorequisitante;
	}

	public String getCrm() {
		return crm;
	}

	public void setCrm(String crm) {
		this.crm = crm;
	}

	public String getMedicorecebedor() {
		return medicorecebedor;
	}

	public void setMedicorecebedor(String medicorecebedor) {
		this.medicorecebedor = medicorecebedor;
	}

	public String getCrmrecebedor() {
		return crmrecebedor;
	}

	public void setCrmrecebedor(String crmrecebedor) {
		this.crmrecebedor = crmrecebedor;
	}

	public String getEspecialidadeorigem() {
		return especialidadeorigem;
	}

	public void setEspecialidadeorigem(String especialidadeorigem) {
		this.especialidadeorigem = especialidadeorigem;
	}

	public String getEspecialidadedestino() {
		return especialidadedestino;
	}

	public void setEspecialidadedestino(String especialidadedestino) {
		this.especialidadedestino = especialidadedestino;
	}

	public int getNbam() {
		return nbam;
	}

	public void setNbam(int nbam) {
		this.nbam = nbam;
	}

	public Date getDataresp() {
		return dataresp;
	}

	public void setDataresp(Date dataresp) {
		this.dataresp = dataresp;
	}

	public String getUnidade() {
		return unidade;
	}

	public void setUnidade(String unidade) {
		this.unidade = unidade;
	}
	
	

}
