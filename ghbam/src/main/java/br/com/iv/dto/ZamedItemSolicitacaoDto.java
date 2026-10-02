package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ZamedItemSolicitacaoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String idPrescricao;
    private String origem;
    private String data;
    private String hora;
    private String prescricao;
    private String medicamentoSolicitado;
    private String idzamed;
    private int retirada;
    private String observacao;
    private String lote;
    private boolean farmfez;
}
