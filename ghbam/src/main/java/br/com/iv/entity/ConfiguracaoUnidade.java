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
@Entity(name = "configurarunidade")
public class ConfiguracaoUnidade  extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 5512992300295847017L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idconfiguracao;
	@Column(length = 60)
	private String prefeitura;
	@Column(length = 18)
	private String complemetos;
	@Column(length = 60)
	private String hospital;
	@Column(length = 60)
	private String hospitalBPA; // máximo de 30 caracteres. Se faltar completar com espaço em branco
	@Column(length = 30)
	private String municipioPadrao;
	@Column(length = 30)
	private String bairroPadrao;
	@Column(length = 15)
	private String sigla;
	// Agrupa unidades do mesmo municipio/gestor para a integracao SUS (ver
	// IntegracaoSusCredencialService): quando preenchido, unidades com o mesmo codigo
	// compartilham a mesma credencial/certificado, em vez de cadastrar um por unidade.
	// Vazio (padrao) preserva o comportamento anterior - cada unidade e "gestora de si".
	@Column(name = "codigo_gestor", length = 30)
	private String codigoGestor;
	@Column(length = 15)
	private String cnesDoHospital;
	@Column(length = 18)
	private String cnpjDoHospital;
	@Column(length = 15)
	private String setorPadrao;
	@Column(length = 30)
	private String tipoUnidade;
	private int codIbge;
	private int registroBPA;
	@Column(length = 40)
	private String orgaoDestino; // máximo de 40 caracteres. Se faltar completar com espaço em branco
	@Column(length = 5)
	private String indicadorBPA;
	@Column(length = 10)
	private String versaoBPA;
	@Column(length = 15)
	private Double longitude;
	@Column(length = 15)
	private Double latitude;
	@Column(length = 15)
	private String cep;
	@Column(length = 60)
	private String urlconexaoimagempacs;
	@Column(length = 60)
	private String urlconexaolaudopacs;
	@Column(length = 500)
	private String tokenimagempacs;
	@Column(length = 500)
	private String tokenlaudopacs;
	@Column(length = 60)
	private String urlConexao;
	@Column(length = 30)
	private String userConexao;
	@Column(length = 15)
	private String pwdConexao;
	@Column(columnDefinition = "TEXT")
	private String logoprefeitura;
	@Column(columnDefinition = "TEXT")
	private String brasaoprefeitura;
	@Column(columnDefinition = "TEXT")
	private String linkvideopainel;
	
	public ConfiguracaoUnidade() {

		limpar();
	}

	public void limpar() {
		this.prefeitura = "";
		this.complemetos = "";
		this.hospital = "";
		this.hospitalBPA = "";
		this.municipioPadrao = "";
		this.bairroPadrao = "";
		this.sigla = "HMSFX";
		this.codigoGestor = "";
		this.cnesDoHospital = "2284634";
		this.cnpjDoHospital = "0";
		this.setorPadrao = "";
		this.tipoUnidade = "HOSPITAL";
		this.codIbge = 330200;
		this.registroBPA = 0;
		this.orgaoDestino = "";
		this.indicadorBPA = "";
		this.versaoBPA = "";
		this.longitude = 0.0;
		this.latitude = 0.0;
		this.cep = "23815310";
		this.logoprefeitura = "logoPrefeiturait.gif";
		//this.brasaoprefeitura = "brasaoit.jpg";
		this.urlconexaolaudopacs = "http://ris.itaguai.rj.gov.br:9596/api/laudopdf/";
		this.urlconexaoimagempacs = "http://pacs.itaguai.rj.gov.br:9596/viewer_ext/estudo/";
		this.tokenimagempacs = "";
		this.tokenlaudopacs = "";
		this.urlConexao = "jdbc:postgresql://127.0.0.1:5432/hospitalit";
		this.userConexao = "";
		this.pwdConexao = "";
		this.linkvideopainel= "";
		
		
		
	}
}
