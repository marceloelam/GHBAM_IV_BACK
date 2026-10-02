package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class TransmitirRemessaDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String protocolo;
    private String observacao;
}
