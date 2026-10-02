package br.com.iv.rag2.dto;

import java.util.List;

import lombok.Data;

@Data
public class Rag2PacienteBuscaResultadoDto {
    private List<Rag2PacienteBuscaDto> itens;
    private int total;
    private int pagina;
    private int limite;
}
