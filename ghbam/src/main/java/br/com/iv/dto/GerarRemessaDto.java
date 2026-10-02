package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class GerarRemessaDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idcompetencia;
    private String tipo;
    private String unidade;
}
