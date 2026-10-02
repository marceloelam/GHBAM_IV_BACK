package br.com.iv.dto;

public record ResumoExportacaoDto(
        String tipo,
        int idcompetencia,
        long quantidadeElegivel,
        double valorEstimado,
        boolean layoutDisponivel) {
}
