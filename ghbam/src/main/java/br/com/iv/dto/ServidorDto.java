package br.com.iv.dto;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Data;

@Data
public class ServidorDto implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idservidor;
	private String matricula;
	@Column(length = 55)
	private String nome;
	@Column(length = 10)
	private String nascimento;
	@Column(length = 16)
	private String cpf;
	@Column(length = 15)
	private String tel;
	@Column(length = 55)
	private String email;
	@Column(length = 50)
	private String pwd;
	@Column(length = 30)
	private String unidade;
	@Column(length = 16)
	private String cns;
	private Boolean recepcao;
	private Boolean triagem;
	private Boolean administracao;
	private Boolean subadm;
	private Boolean medico;
	private Boolean dentista;
	private Boolean danfe;
	private Boolean rx;
	private Boolean laboratorio;
	private Boolean farmacia;
	private Boolean faturamento;
	private Boolean remedios;
	private Boolean farmhmsfx;
	private Boolean farmcentral;
	private Boolean medicacao;
	private Boolean enfermagem;
	private Boolean tcenfermagem;
	private Boolean assistencia;
	private Boolean fisioterapia;
	private Boolean nutricao;
	private Boolean fonoaudiologia;
	private Boolean psicologia;
	private Boolean criaragenda;
	private Boolean agendarpaciente;
	private Boolean consultorioadmin;
	private Boolean consultoriorecepcao;
	private Boolean consultoriomedico;
	private Boolean consultoriofinanceiro;
	private Boolean tomografia;
	private Boolean ultrassonografia;
	private Boolean rh;
	private Boolean ativo;
	@Column(length = 15)
	private String cargo;
	@Column(length = 10)
	private String corem;
	@Column(length = 10)
	private String cbo;
	@Column(length = 10)
	private String crm;
	@Column(length = 15)
	private String especialidade;
	@Column(columnDefinition = "TEXT")
	private String foto;
	@Column(columnDefinition = "TEXT")
	private String biometria;
	@Column(columnDefinition = "TEXT")
	private String fir;
	private String usuarioad;
	private String certificadoPfxBase64;
	private String certificadoPfxSenha;
	private String certificadoTitular;
	private String certificadoEmissor;
	private String certificadoValidade;

}
