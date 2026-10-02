package br.com.iv.dto;

import br.com.iv.enums.ResultadoValidacao;

public record ValidacaoResultado(
        String codigo,
        String campo,
        ResultadoValidacao resultado,
        String mensagem,
        String valorEncontrado,
        String valorEsperado) {
}
