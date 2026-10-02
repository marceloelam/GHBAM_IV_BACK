package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ZamedPeriodoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String inicio;
    private String fim;
}
