package br.com.iv.cdss.dto;

import java.time.LocalDateTime;

/** Valor critico de um item de laudo laboratorial e a ciencia registrada (se houver). */
public record ResultadoCriticoDto(
        long iditem,
        int idpedidoresultado,
        int bam,
        String exame,
        String valor,
        String unidadeMedida,
        String analito,
        String direcao,
        double limite,
        String dataResultado,
        boolean ciente,
        LocalDateTime cienciaEm,
        String cienciaNome,
        String cienciaConduta) {
}
