package br.com.iv.cdss.dto;

/** Passagem de plantao (SBAR) de um leito. Autor e unidade vem da sessao. */
public class PassagemPlantaoRequest {
    private Integer idbam;
    private String situacao;
    private String historico;
    private String avaliacao;
    private String recomendacao;

    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public String getSituacao() { return situacao; }
    public void setSituacao(String situacao) { this.situacao = situacao; }
    public String getHistorico() { return historico; }
    public void setHistorico(String historico) { this.historico = historico; }
    public String getAvaliacao() { return avaliacao; }
    public void setAvaliacao(String avaliacao) { this.avaliacao = avaliacao; }
    public String getRecomendacao() { return recomendacao; }
    public void setRecomendacao(String recomendacao) { this.recomendacao = recomendacao; }
}
