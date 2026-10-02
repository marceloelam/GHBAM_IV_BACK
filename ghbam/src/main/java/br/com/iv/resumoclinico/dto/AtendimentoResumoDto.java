package br.com.iv.resumoclinico.dto;
public record AtendimentoResumoDto(int bam, String data, String hora, String unidade, boolean emAtendimento, boolean internado, boolean alta, String queixaPrincipal) {}
