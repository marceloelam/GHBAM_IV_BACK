package br.com.iv.dto;

import jakarta.persistence.Column;

public class PedidoItemDTO {
    private String material;
    private Integer quantidade;
    private String observacao;
    @Column(columnDefinition = "integer default 0")
    private int quantidadeliberada;
  //  @Column(columnDefinition = "varchar(20) default ''")
    @Column(columnDefinition = "TEXT DEFAULT ''")
    private String msgresposta;
    @Column(columnDefinition = "TEXT DEFAULT ''")
    private String ci;
    private boolean recebido;

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
	public int getQuantidadeliberada() {
		return quantidadeliberada;
	}
	public void setQuantidadeliberada(int quantidadeliberada) {
		this.quantidadeliberada = quantidadeliberada;
	}
	public String getMsgresposta() {
		return msgresposta;
	}
	public void setMsgresposta(String msgresposta) {
		this.msgresposta = msgresposta;
	}
	public String getCi() {
		return ci;
	}
	public void setCi(String ci) {
		this.ci = ci;
	}
	public boolean isRecebido() {
		return recebido;
	}
	public void setRecebido(boolean recebido) {
		this.recebido = recebido;
	}
    
    
}
