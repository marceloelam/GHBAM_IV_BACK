package br.com.iv.dto;

public record DiagnosticoExportacaoDto(
        String instrumento,
        long total,
        long aptosNaoExportados,
        long aptosExportados,
        long comCritica,
        long pendentes,
        long naoFaturavel,
        double valorAptoNaoExportado) {
}
