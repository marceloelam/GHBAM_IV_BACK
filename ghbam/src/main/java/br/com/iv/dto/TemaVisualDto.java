package br.com.iv.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

@Data
public class TemaVisualDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long idTemaVisual;
    private String unidade;
    private String primaria;
    private String secundaria;
    private Boolean gradiente;
    private Map<String, String> papeis;
    private String usuario;
    private Boolean persistidoNoBackend;
}
