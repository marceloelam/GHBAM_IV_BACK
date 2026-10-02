package br.com.iv.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SigtapArquivoDatasusDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nome;
    private String competencia;
    private String versao;
    private String url;
    private long tamanho;
    private String dataArquivo;
}
