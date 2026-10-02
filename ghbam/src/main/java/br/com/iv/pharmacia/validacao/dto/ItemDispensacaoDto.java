package br.com.iv.pharmacia.validacao.dto;

/**
 * Um item que a farmacia esta prestes a dispensar. O frontend monta esta lista
 * a partir do carrinho de dispensacao (lotes selecionados) antes de confirmar.
 */
public class ItemDispensacaoDto {

    private Long loteId;
    private Long produtoId;
    /** Nome do produto como aparece no estoque. Usado no casamento com a base de conhecimento. */
    private String produtoNome;
    /** Principio ativo cadastrado no produto. Quando presente, tem prioridade sobre o nome comercial. */
    private String principioAtivo;
    private String formaFarmaceutica;
    private String concentracao;
    private Integer quantidade;
    /** Dias de tratamento previstos. */
    private Integer duracao;
    /** Posologia vinda da prescricao estruturada, por exemplo 500 mg. */
    private String dose;
    /** Intervalo, por exemplo 8/8h ou a cada 6 horas. */
    private String frequencia;
    private String via;

    public Long getLoteId() { return loteId; }
    public void setLoteId(Long loteId) { this.loteId = loteId; }
    public Long getProdutoId() { return produtoId; }
    public void setProdutoId(Long produtoId) { this.produtoId = produtoId; }
    public String getProdutoNome() { return produtoNome; }
    public void setProdutoNome(String produtoNome) { this.produtoNome = produtoNome; }
    public String getPrincipioAtivo() { return principioAtivo; }
    public void setPrincipioAtivo(String principioAtivo) { this.principioAtivo = principioAtivo; }
    public String getFormaFarmaceutica() { return formaFarmaceutica; }
    public void setFormaFarmaceutica(String formaFarmaceutica) { this.formaFarmaceutica = formaFarmaceutica; }
    public String getConcentracao() { return concentracao; }
    public void setConcentracao(String concentracao) { this.concentracao = concentracao; }
    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
    public Integer getDuracao() { return duracao; }
    public void setDuracao(Integer duracao) { this.duracao = duracao; }
    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }
    public String getFrequencia() { return frequencia; }
    public void setFrequencia(String frequencia) { this.frequencia = frequencia; }
    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }

    /** Nome que melhor identifica a substancia: principio ativo quando cadastrado, senao o nome do produto. */
    public String identificacaoClinica() {
        if (principioAtivo != null && !principioAtivo.isBlank()) {
            return principioAtivo;
        }
        return produtoNome == null ? "" : produtoNome;
    }
}
