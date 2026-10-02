package br.com.iv.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pedido_item")
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false)
    private String material;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(length = 2000)
    private String observacao;
    
    @Column(columnDefinition = "integer default 0")
    private int quantidadeliberada;
  //  @Column(columnDefinition = "varchar(20) default ''")
    @Column(columnDefinition = "TEXT DEFAULT ''")
    private String msgresposta;
    @Column(columnDefinition = "TEXT DEFAULT ''")
    private String ci;
    private boolean recebido;
    
    
    
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
	public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
	public boolean isRecebido() {
		return recebido;
	}
	public void setRecebido(boolean recebido) {
		this.recebido = recebido;
	}


}
