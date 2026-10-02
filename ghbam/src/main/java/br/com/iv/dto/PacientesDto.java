package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.Column;
import lombok.Data;
@Data
public class PacientesDto implements Serializable {
	private static final long serialVersionUID = 1L;
	@Column(length = 55, nullable = false) // nullable=false - Não pode ser nulo
	private String nome;
	@Column(length = 10, nullable = false)
	private String nascimento;
	@Column(length = 25)
	private String profissao;
	@Column(length = 1)
	private String sexo;
	@Column(length = 16)
	private String etnia;
	@Column(length = 55, nullable = false)
	private String mae;
	@Column(length = 55)
	private String pai;
	@Column(length = 19)
	private String cns;
	@Column(length = 16)
	private String cpf;
	@Column(length = 16)
	private String rg;
	@Column(length = 15)
	private String ecivil;
	@Column(length = 30)
	private String naturalidade;
	@Column(length = 75)
	private String endereco;
	@Column(length = 15)
	private String numero;
	@Column(length = 55)
	private String bairro;
	@Column(length = 10)
	private String cep;
	@Column(length = 2)
	private String estado;
	@Column(length = 40)
	private String telefone;
	@Column(length = 5)
	private String sangue;
	@Column(length = 55)
	private String municipio;
	private int idade;
	@Column(length = 25)
	private String prontuario;
	private double latitude;
	private double longitude;
	private String tempcod;
	public static String value = "À Á Â Ã Ä Å Æ Ç È É Ê Ë Ì Í Î Ï Ð Ñ Ò Ó Ô Õ Ö Ø Ù Ú Û Ü Ý Þ ß à á â ã ä å æ ç è é ê ë ì í î ï ð ñ ò ó ô õ ö ø ù ú û ü ý þ ÿ ";
	private LocalDate data;
	@Column(length = 5)
	private String hora;
	private boolean sic;
	@Column(length = 30)
	private String pacienteespecial;
}
