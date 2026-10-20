package br.com.iv.cdss.dto;

/** Insercao de dispositivo invasivo num leito. Autor e unidade vem da sessao. */
public class DispositivoInvasivoRequest {
    private Integer idbam;
    private String tipo;
    private String sitio;
    /** aaaa-mm-ddThh:mm; vazio = agora. */
    private String inseridoEm;

    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getSitio() { return sitio; }
    public void setSitio(String sitio) { this.sitio = sitio; }
    public String getInseridoEm() { return inseridoEm; }
    public void setInseridoEm(String inseridoEm) { this.inseridoEm = inseridoEm; }
}
