package br.com.iv.dto;

// valorEstimado e o total da unidade na competencia (todos os status, como sempre foi).
// valorApto e o que sai na exportacao; valorRetido e o que tem valor mas depende de
// correcao (critica, revisao ou pendente) - sem essa separacao a coluna de valor sempre
// parecia maior do que o que seria pago.
public record UnidadeResumoDto(String unidade, long total, long aptos, long criticas, double valorEstimado,
        double valorApto, double valorRetido) {
}
