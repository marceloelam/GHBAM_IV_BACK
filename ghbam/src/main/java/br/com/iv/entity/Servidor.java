package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.xml.bind.annotation.XmlRootElement;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
@Entity(name = "servidor")
@XmlRootElement
public class Servidor extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 8697783797202417749L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idservidor;
	@Column(length = 15, nullable = false, unique = true)
	private String matricula;
	@Column(length = 55)
	private String nome;
	// Nascimento (dd/MM/yyyy) e CPF: o par que o cadastro publico do CNES exige para
	// devolver o CNS do profissional. Texto no formato exibido na tela, igual ao
	// cadastro de pacientes - um segundo formato de data aqui so criaria leitura
	// invertida mais tarde.
	@Column(length = 10)
	private String nascimento;
	@Column(length = 16)
	private String cpf;
	@Column(length = 15)
	private String tel;
	@Column(length = 55)
	private String email;
	// Nunca deve sair em JSON — nem em hash. So o backend le/escreve isso,
	// e so pra comparar (SecurityPermissionService) ou trocar (ServidorService).
	@JsonIgnore
	@Column(length = 255)
	private String pwd;
	@Column(length = 30)
	private String unidade;
	@Column(length = 16)
	private String cns;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean recepcao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean triagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean administracao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean medico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean dentista;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean danfe;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean rx;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean laboratorio;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean farmacia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean faturamento;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean remedios;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean farmhmsfx;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean farmcentral;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean medicacao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean enfermagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean tcenfermagem;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean assistencia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean fisioterapia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean nutricao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean fonoaudiologia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean psicologia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean criaragenda;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean agendarpaciente;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultorioadmin;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultoriorecepcao;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultoriomedico;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean consultoriofinanceiro;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean tomografia;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean ultrassonografia;
	@Column(nullable = true, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean subadm;
	@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean rh;
	@Column(nullable = true, columnDefinition = "BOOLEAN DEFAULT TRUE")
	private Boolean ativo;
	@Column(length = 15)
	private String cargo;
	@Column(length = 18)
	private String corem;
	@Column(length = 10)
	private String cbo;
	@Column(length = 18)
	private String crm;
	@Column(length = 15)
	private String especialidade;
	@Column(columnDefinition = "TEXT")
	private String foto;
	@Column(columnDefinition = "TEXT")
	private String biometria;
	@Column(columnDefinition = "TEXT")
	private String fir;
	@Column(length = 30)
	private String usuarioad;
	@Column(columnDefinition = "TEXT")
	private String certificadoPfxBase64;
	@Column(length = 200)
	private String certificadoPfxSenha;
	@Column(length = 200)
	private String certificadoTitular;
	@Column(length = 200)
	private String certificadoEmissor;
	@Column(length = 10)
	private String certificadoValidade;
	@Column(name = "celular_pareado_ativo", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	private Boolean celularPareadoAtivo = false;
	@Column(name = "celular_pareado_nome", length = 120)
	private String celularPareadoNome;
	@Column(name = "celular_pareado_em")
	private java.time.LocalDateTime celularPareadoEm;
	// Definir padrão das variáveis

	public Servidor() {
		super();
		iniciarvariavel();
	}

	public void iniciarvariavel() {
		idservidor = 0;
		matricula = "";
		nome = "";
		nascimento = "";
		cpf = "";
		tel = "";
		email = "";
		pwd = "123456";
		recepcao = false;
		triagem = false;
		administracao = false;
		subadm = false;
		medico = false;
		dentista = false;
		danfe = false;
		rx = false;
		farmacia = false;
		remedios = false;
		laboratorio = false;
		faturamento = false;
		farmhmsfx = false;
		farmcentral = false;
		psicologia = false;
		medicacao = false;
		enfermagem = false;
		tomografia=false;
		ultrassonografia=false;
		tcenfermagem = false;
		assistencia = false;
		fisioterapia = false;
		nutricao = false;
		fonoaudiologia = false;
		criaragenda = false;
		agendarpaciente = false;
		consultorioadmin = false;
		consultoriorecepcao = false;
		consultoriomedico = false;
		consultoriofinanceiro = false;
		rh = false;
		ativo = true;
		cargo = "SERVIDOR";
		corem = "";
		crm = "";
		especialidade = "";
		unidade = "";
		cbo = "";
		foto = "";
		biometria="";
		fir="";
		usuarioad = "";
		certificadoPfxBase64 = null;
		certificadoPfxSenha = null;
		certificadoTitular = null;
		certificadoEmissor = null;
		certificadoValidade = null;
//ODUyNDU2
	}

}
