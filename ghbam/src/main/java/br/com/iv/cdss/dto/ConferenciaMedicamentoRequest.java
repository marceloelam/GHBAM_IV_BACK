package br.com.iv.cdss.dto;

/** Leitura da embalagem na checagem a beira-leito. Unidade e autor vem da sessao. */
public class ConferenciaMedicamentoRequest {
    private String codigoBarras;
    private Integer idbam;
    private Integer idpaciente;
    private String textoPrescricao;

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public Integer getIdpaciente() { return idpaciente; }
    public void setIdpaciente(Integer idpaciente) { this.idpaciente = idpaciente; }
    public String getTextoPrescricao() { return textoPrescricao; }
    public void setTextoPrescricao(String textoPrescricao) { this.textoPrescricao = textoPrescricao; }
}
