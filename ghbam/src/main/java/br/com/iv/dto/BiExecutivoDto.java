package br.com.iv.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BiExecutivoDto {
	private String unidade;
	private String inicio;
	private String fim;
	private List<Indicador> indicadores = new ArrayList<>();
	private List<Serie> ocupacaoPorSetor = new ArrayList<>();
	private List<Serie> produtividadePorSetor = new ArrayList<>();
	private List<Serie> tendenciaAtendimentos = new ArrayList<>();
	private List<Alerta> alertas = new ArrayList<>();
	private List<DetalheCcih> detalhesCcih = new ArrayList<>();
	private List<DetalheIndicador> detalhesIndicadores = new ArrayList<>();

	public String getUnidade() {
		return unidade;
	}

	public void setUnidade(String unidade) {
		this.unidade = unidade;
	}

	public String getInicio() {
		return inicio;
	}

	public void setInicio(String inicio) {
		this.inicio = inicio;
	}

	public String getFim() {
		return fim;
	}

	public void setFim(String fim) {
		this.fim = fim;
	}

	public List<Indicador> getIndicadores() {
		return indicadores;
	}

	public void setIndicadores(List<Indicador> indicadores) {
		this.indicadores = indicadores;
	}

	public List<Serie> getOcupacaoPorSetor() {
		return ocupacaoPorSetor;
	}

	public void setOcupacaoPorSetor(List<Serie> ocupacaoPorSetor) {
		this.ocupacaoPorSetor = ocupacaoPorSetor;
	}

	public List<Serie> getProdutividadePorSetor() {
		return produtividadePorSetor;
	}

	public void setProdutividadePorSetor(List<Serie> produtividadePorSetor) {
		this.produtividadePorSetor = produtividadePorSetor;
	}

	public List<Serie> getTendenciaAtendimentos() {
		return tendenciaAtendimentos;
	}

	public void setTendenciaAtendimentos(List<Serie> tendenciaAtendimentos) {
		this.tendenciaAtendimentos = tendenciaAtendimentos;
	}

	public List<Alerta> getAlertas() {
		return alertas;
	}

	public void setAlertas(List<Alerta> alertas) {
		this.alertas = alertas;
	}

	public List<DetalheCcih> getDetalhesCcih() {
		return detalhesCcih;
	}

	public void setDetalhesCcih(List<DetalheCcih> detalhesCcih) {
		this.detalhesCcih = detalhesCcih;
	}

	public List<DetalheIndicador> getDetalhesIndicadores() {
		return detalhesIndicadores;
	}

	public void setDetalhesIndicadores(List<DetalheIndicador> detalhesIndicadores) {
		this.detalhesIndicadores = detalhesIndicadores;
	}

	public static class Indicador {
		private String chave;
		private String titulo;
		private BigDecimal valor;
		private String unidadeMedida;
		private String status;
		private String detalhe;
		private boolean disponivel = true;

		public Indicador() {
		}

		public Indicador(String chave, String titulo, BigDecimal valor, String unidadeMedida, String status, String detalhe) {
			this.chave = chave;
			this.titulo = titulo;
			this.valor = valor;
			this.unidadeMedida = unidadeMedida;
			this.status = status;
			this.detalhe = detalhe;
		}

		public static Indicador indisponivel(String chave, String titulo, String detalhe) {
			Indicador indicador = new Indicador(chave, titulo, null, "", "indisponivel", detalhe);
			indicador.setDisponivel(false);
			return indicador;
		}

		public String getChave() {
			return chave;
		}

		public void setChave(String chave) {
			this.chave = chave;
		}

		public String getTitulo() {
			return titulo;
		}

		public void setTitulo(String titulo) {
			this.titulo = titulo;
		}

		public BigDecimal getValor() {
			return valor;
		}

		public void setValor(BigDecimal valor) {
			this.valor = valor;
		}

		public String getUnidadeMedida() {
			return unidadeMedida;
		}

		public void setUnidadeMedida(String unidadeMedida) {
			this.unidadeMedida = unidadeMedida;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getDetalhe() {
			return detalhe;
		}

		public void setDetalhe(String detalhe) {
			this.detalhe = detalhe;
		}

		public boolean isDisponivel() {
			return disponivel;
		}

		public void setDisponivel(boolean disponivel) {
			this.disponivel = disponivel;
		}
	}

	public static class Serie {
		private String label;
		private BigDecimal valor;
		private BigDecimal referencia;
		private String detalhe;

		public Serie() {
		}

		public Serie(String label, BigDecimal valor, BigDecimal referencia, String detalhe) {
			this.label = label;
			this.valor = valor;
			this.referencia = referencia;
			this.detalhe = detalhe;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public BigDecimal getValor() {
			return valor;
		}

		public void setValor(BigDecimal valor) {
			this.valor = valor;
		}

		public BigDecimal getReferencia() {
			return referencia;
		}

		public void setReferencia(BigDecimal referencia) {
			this.referencia = referencia;
		}

		public String getDetalhe() {
			return detalhe;
		}

		public void setDetalhe(String detalhe) {
			this.detalhe = detalhe;
		}
	}

	public static class Alerta {
		private String tipo;
		private String titulo;
		private String detalhe;

		public Alerta() {
		}

		public Alerta(String tipo, String titulo, String detalhe) {
			this.tipo = tipo;
			this.titulo = titulo;
			this.detalhe = detalhe;
		}

		public String getTipo() {
			return tipo;
		}

		public void setTipo(String tipo) {
			this.tipo = tipo;
		}

		public String getTitulo() {
			return titulo;
		}

		public void setTitulo(String titulo) {
			this.titulo = titulo;
		}

		public String getDetalhe() {
			return detalhe;
		}

		public void setDetalhe(String detalhe) {
			this.detalhe = detalhe;
		}
	}

	public static class DetalheCcih {
		private String bam;
		private String paciente;
		private String dataAlta;
		private String cid;
		private String diagnostico;
		private String germesIsolados;
		private String antibiotico;

		public DetalheCcih() {
		}

		public DetalheCcih(String bam, String paciente, String dataAlta, String cid, String diagnostico,
				String germesIsolados, String antibiotico) {
			this.bam = bam;
			this.paciente = paciente;
			this.dataAlta = dataAlta;
			this.cid = cid;
			this.diagnostico = diagnostico;
			this.germesIsolados = germesIsolados;
			this.antibiotico = antibiotico;
		}

		public String getBam() {
			return bam;
		}

		public void setBam(String bam) {
			this.bam = bam;
		}

		public String getPaciente() {
			return paciente;
		}

		public void setPaciente(String paciente) {
			this.paciente = paciente;
		}

		public String getDataAlta() {
			return dataAlta;
		}

		public void setDataAlta(String dataAlta) {
			this.dataAlta = dataAlta;
		}

		public String getCid() {
			return cid;
		}

		public void setCid(String cid) {
			this.cid = cid;
		}

		public String getDiagnostico() {
			return diagnostico;
		}

		public void setDiagnostico(String diagnostico) {
			this.diagnostico = diagnostico;
		}

		public String getGermesIsolados() {
			return germesIsolados;
		}

		public void setGermesIsolados(String germesIsolados) {
			this.germesIsolados = germesIsolados;
		}

		public String getAntibiotico() {
			return antibiotico;
		}

		public void setAntibiotico(String antibiotico) {
			this.antibiotico = antibiotico;
		}
	}

	public static class DetalheIndicador {
		private String indicador;
		private String bam;
		private String paciente;
		private String setor;
		private String data;
		private String valor;
		private String detalhe;

		public DetalheIndicador() {
		}

		public DetalheIndicador(String indicador, String bam, String paciente, String setor, String data, String valor,
				String detalhe) {
			this.indicador = indicador;
			this.bam = bam;
			this.paciente = paciente;
			this.setor = setor;
			this.data = data;
			this.valor = valor;
			this.detalhe = detalhe;
		}

		public String getIndicador() {
			return indicador;
		}

		public void setIndicador(String indicador) {
			this.indicador = indicador;
		}

		public String getBam() {
			return bam;
		}

		public void setBam(String bam) {
			this.bam = bam;
		}

		public String getPaciente() {
			return paciente;
		}

		public void setPaciente(String paciente) {
			this.paciente = paciente;
		}

		public String getSetor() {
			return setor;
		}

		public void setSetor(String setor) {
			this.setor = setor;
		}

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}

		public String getValor() {
			return valor;
		}

		public void setValor(String valor) {
			this.valor = valor;
		}

		public String getDetalhe() {
			return detalhe;
		}

		public void setDetalhe(String detalhe) {
			this.detalhe = detalhe;
		}
	}
}
