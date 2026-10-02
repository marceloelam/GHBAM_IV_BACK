package br.com.iv.resumoclinico.dto;

public record ProgressoResumoClinicoResponse(
    String processoId,
    String status,
    String fase,
    String mensagem,
    int percentual,
    ResumoClinicoResponse resultado,
    String erro
) {}
