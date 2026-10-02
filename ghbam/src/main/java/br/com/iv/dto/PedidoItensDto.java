package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import br.com.iv.entity.Pedido;
import jakarta.persistence.Column;
import lombok.Data;

@Data
public class PedidoItensDto implements Serializable {
	private static final long serialVersionUID = 1L;
	
    private Long id;
    private String unidade;
    private String unidadeSigla;
    private String comentario;
    private LocalDate criadoEm;
	
    private Pedido pedido;
    private String material;
    private Integer quantidade;
    private String observacao;


    private int quantidadeliberada;
    private String msgresposta;
    private String ci;
    
	private boolean recebido;
	
}
