package br.com.iv.cdss.dto;

/** Ciencia do profissional sobre um valor critico; autor vem da sessao, nunca do corpo. */
public class CienciaResultadoCriticoRequest {
    private Integer idbam;
    private Long iditem;
    private String conduta;

    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public Long getIditem() { return iditem; }
    public void setIditem(Long iditem) { this.iditem = iditem; }
    public String getConduta() { return conduta; }
    public void setConduta(String conduta) { this.conduta = conduta; }
}
