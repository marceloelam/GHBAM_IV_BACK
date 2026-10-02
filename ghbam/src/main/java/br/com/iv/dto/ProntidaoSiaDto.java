package br.com.iv.dto;

import java.util.List;

public record ProntidaoSiaDto(
        int idcompetencia,
        String competencia,
        String statusCompetencia,
        String unidade,
        boolean credencialConfigurada,
        boolean transmissaoDiretaAtiva,
        String orientacaoTransmissao,
        List<ModuloSiaDto> modulos) {

    public record ModuloSiaDto(
            String codigo,
            String nome,
            String operacaoGhbam,
            String situacao,
            long total,
            long aptos,
            long criticas,
            long pendentes,
            long naoFaturavel,
            double valorApto,
            boolean layoutDisponivel,
            boolean podeGerar,
            String orientacao) {
    }
}
