package br.com.iv.dto;

import lombok.Data;

@Data
public class MunicipioCountDTO {
    private String municipio;
    private Long total;
    
    public MunicipioCountDTO(String municipio, Long total) {
        this.municipio = municipio;
        this.total = total;
    }
    
  
}