package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class ConfiguracaoUnidadeDto   implements Serializable {
	private static final long serialVersionUID = 1L;
		
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
	@Column(length = 30)
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
	

}
