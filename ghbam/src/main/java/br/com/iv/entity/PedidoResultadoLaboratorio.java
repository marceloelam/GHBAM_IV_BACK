package br.com.iv.entity;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity(name = "PedidoResultadoLaboratorio")
public class PedidoResultadoLaboratorio extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L; // = 344185911652747414L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idPedidoResultado;
	@Column(length = 65) 
	private String medico;
	@Column(length = 25) 
	private String crm;
	@Column(length = 25) 
	private String setor;
	@Column(length = 25) 
	private String local;
	private int bam;
	@Column(columnDefinition = "TEXT")
	private String arquivo;
	// Com o MinIO ligado, arquivo fica vazio no banco e o laudo esta nesta chave.
	@JsonIgnore
	@Column(name = "armazenamento_chave", length = 255)
	private String armazenamentoChave;
	@Column(length = 12)
	private String dt;
	@Column(length = 5)
	private String hr;
	@Column(length = 25)
	private String unidade;
	@Column(length = 35)
	private String descricao;
	@Column(length = 10000)
	private String grupopedidos;
	@Column(name = "valor_estruturado", columnDefinition = "TEXT")
	private String valorEstruturado;
	@Column(name = "unidade_medida", length = 50)
	private String unidadeMedida;
	@Column(name = "referencia_valor", length = 120)
	private String referenciaValor;
	@Column(name = "amostra_biologica", length = 120)
	private String amostraBiologica;
	@Column(name = "data_coleta", length = 10)
	private String dataColeta;
	@Column(name = "codigo_loinc", length = 30)
	private String codigoLoinc;
	@Column(name = "justificativa_ausencia_valor", columnDefinition = "TEXT")
	private String justificativaAusenciaValor;

	public PedidoResultadoLaboratorio() {
		super();
		bam = 0;
		setor = "";
		medico = "";
		crm = "";
		unidade = "";
		local = "";
		arquivo = "";
		descricao = "";
		crm = "";

		dt = converteData();
		hr = converteTime();

	}

	protected String converteData() {
		Date dataAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("yyyy/MM/dd");
		String dataFormatada = formatador.format(dataAtual);

		return dataFormatada;
	}

	protected String converteTime() {
		Date horaAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("HH:mm");
		String timeFormatada = formatador.format(horaAtual);

		return timeFormatada;
	}

}
