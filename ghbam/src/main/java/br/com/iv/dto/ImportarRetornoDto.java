package br.com.iv.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class ImportarRetornoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idremessa;
    private String competencia;
    private String unidade;
    private String observacao;
    private List<RetornoItemImportDto> itens;
}
