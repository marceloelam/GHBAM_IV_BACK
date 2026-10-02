package br.com.iv.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class SigtapImportacaoRequestDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String competencia;
    private String unidade;
    private String observacao;
    private List<SigtapProcedimentoImportDto> procedimentos;
}
