package br.com.iv.cdss.dto;

/** Decisao do prescritor sobre um medicamento de uso continuo neste BAM. Autor vem da sessao. */
public class ReconciliacaoDecisaoRequest {
    private Integer idbam;
    private Integer idpaciente;
    private Integer iditem;
    private String decisao;
    private String observacao;

    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public Integer getIdpaciente() { return idpaciente; }
    public void setIdpaciente(Integer idpaciente) { this.idpaciente = idpaciente; }
    public Integer getIditem() { return iditem; }
    public void setIditem(Integer iditem) { this.iditem = iditem; }
    public String getDecisao() { return decisao; }
    public void setDecisao(String decisao) { this.decisao = decisao; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
