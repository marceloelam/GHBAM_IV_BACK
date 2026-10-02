package br.com.iv.cdss.dto;

public class News2Request {
    private Integer frequenciaRespiratoria;
    private Integer saturacaoOxigenio;
    private Boolean oxigenioSuplementar;
    private Double temperatura;
    private Integer pressaoSistolica;
    private Integer frequenciaCardiaca;
    private String consciencia;

    public Integer getFrequenciaRespiratoria() { return frequenciaRespiratoria; }
    public void setFrequenciaRespiratoria(Integer frequenciaRespiratoria) { this.frequenciaRespiratoria = frequenciaRespiratoria; }
    public Integer getSaturacaoOxigenio() { return saturacaoOxigenio; }
    public void setSaturacaoOxigenio(Integer saturacaoOxigenio) { this.saturacaoOxigenio = saturacaoOxigenio; }
    public Boolean getOxigenioSuplementar() { return oxigenioSuplementar; }
    public void setOxigenioSuplementar(Boolean oxigenioSuplementar) { this.oxigenioSuplementar = oxigenioSuplementar; }
    public Double getTemperatura() { return temperatura; }
    public void setTemperatura(Double temperatura) { this.temperatura = temperatura; }
    public Integer getPressaoSistolica() { return pressaoSistolica; }
    public void setPressaoSistolica(Integer pressaoSistolica) { this.pressaoSistolica = pressaoSistolica; }
    public Integer getFrequenciaCardiaca() { return frequenciaCardiaca; }
    public void setFrequenciaCardiaca(Integer frequenciaCardiaca) { this.frequenciaCardiaca = frequenciaCardiaca; }
    public String getConsciencia() { return consciencia; }
    public void setConsciencia(String consciencia) { this.consciencia = consciencia; }
}
