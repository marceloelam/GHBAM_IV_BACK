package br.com.iv.cdss.dto;

public class CdssBularioMedicamentoDto {
    private Integer id;
    private String nome;
    private String principioAtivo;
    private String posologia;
    private String contraindicacoes;
    private String interacoes;
    private String precaucoes;
    private String fonte;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getPrincipioAtivo() { return principioAtivo; }
    public void setPrincipioAtivo(String principioAtivo) { this.principioAtivo = principioAtivo; }
    public String getPosologia() { return posologia; }
    public void setPosologia(String posologia) { this.posologia = posologia; }
    public String getContraindicacoes() { return contraindicacoes; }
    public void setContraindicacoes(String contraindicacoes) { this.contraindicacoes = contraindicacoes; }
    public String getInteracoes() { return interacoes; }
    public void setInteracoes(String interacoes) { this.interacoes = interacoes; }
    public String getPrecaucoes() { return precaucoes; }
    public void setPrecaucoes(String precaucoes) { this.precaucoes = precaucoes; }
    public String getFonte() { return fonte; }
    public void setFonte(String fonte) { this.fonte = fonte; }
}
