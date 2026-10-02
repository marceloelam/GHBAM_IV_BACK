package br.com.iv.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MonitoramentoDto {

    private String unidade;
    private String atualizadoEm;
    private int pacientesAguardando;
    private int casosCriticos;
    private BigDecimal tempoMedioAtendimentoMin;
    private BigDecimal tempoPermanenciaMediaDias;
    private BigDecimal taxaOcupacaoPct;
    private int totalLeitos;
    private int leitosOcupados;
    private List<Alerta> alertas = new ArrayList<>();
    private List<SetorOcupacao> setores = new ArrayList<>();

    public static class Alerta {
        private String nivel;
        private String titulo;
        private String mensagem;

        public Alerta(String nivel, String titulo, String mensagem) {
            this.nivel = nivel;
            this.titulo = titulo;
            this.mensagem = mensagem;
        }

        public String getNivel() { return nivel; }
        public String getTitulo() { return titulo; }
        public String getMensagem() { return mensagem; }
    }

    public static class SetorOcupacao {
        private String setor;
        private int ocupados;
        private int leitos;
        private BigDecimal percentual;

        public SetorOcupacao(String setor, int ocupados, int leitos, BigDecimal percentual) {
            this.setor = setor;
            this.ocupados = ocupados;
            this.leitos = leitos;
            this.percentual = percentual;
        }

        public String getSetor() { return setor; }
        public int getOcupados() { return ocupados; }
        public int getLeitos() { return leitos; }
        public BigDecimal getPercentual() { return percentual; }
    }

    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public String getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(String atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public int getPacientesAguardando() { return pacientesAguardando; }
    public void setPacientesAguardando(int pacientesAguardando) { this.pacientesAguardando = pacientesAguardando; }
    public int getCasosCriticos() { return casosCriticos; }
    public void setCasosCriticos(int casosCriticos) { this.casosCriticos = casosCriticos; }
    public BigDecimal getTempoMedioAtendimentoMin() { return tempoMedioAtendimentoMin; }
    public void setTempoMedioAtendimentoMin(BigDecimal v) { this.tempoMedioAtendimentoMin = v; }
    public BigDecimal getTempoPermanenciaMediaDias() { return tempoPermanenciaMediaDias; }
    public void setTempoPermanenciaMediaDias(BigDecimal v) { this.tempoPermanenciaMediaDias = v; }
    public BigDecimal getTaxaOcupacaoPct() { return taxaOcupacaoPct; }
    public void setTaxaOcupacaoPct(BigDecimal v) { this.taxaOcupacaoPct = v; }
    public int getTotalLeitos() { return totalLeitos; }
    public void setTotalLeitos(int totalLeitos) { this.totalLeitos = totalLeitos; }
    public int getLeitosOcupados() { return leitosOcupados; }
    public void setLeitosOcupados(int leitosOcupados) { this.leitosOcupados = leitosOcupados; }
    public List<Alerta> getAlertas() { return alertas; }
    public List<SetorOcupacao> getSetores() { return setores; }
}
