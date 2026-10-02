package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import lombok.Data;
import br.com.iv.entity.Bam;
import br.com.iv.entity.LocalCenso;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.SetorCenso;

@Data
public class NovoInternadoDto implements Serializable {
	private static final long serialVersionUID = 1L;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private SetorCenso setor;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private LocalCenso local;
	@Column(length = 18)
	private String internado;
	@Column(length = 5)
	private String especialidade;
	@Column(length = 65)
	private String diagnostico;
	private LocalDate diainternacao;
	@Column(length = 20)
	private String prontuario;
	private int tempoDias;
	private LocalDate datainsercaoser;
	private LocalDate datadaacao;
	@Column(length = 20)
	private String leitoocupado;
	@Column(length = 10)
	private String agente;
	@Column(length = 15)
	private String tipo;
	@Column(length = 20)
	private String motivosaida;
	@Column(length = 500)
	private String pendencia;
	@Column(length = 25)
	private String unidade;
	private boolean alta;
	private int nbam;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Pacientes pacientes;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private Bam bam;
	@Column(length = 185)
	private String tipo_alocacao;
	@Column(length = 185)
	private String quemalocou;
	@Column(length = 185)
	private String quemdeualta;
	@Column(length = 185)
	private String quemleitoalterou;
	@Column(length = 185)
	private String quemexcluiu;

}
