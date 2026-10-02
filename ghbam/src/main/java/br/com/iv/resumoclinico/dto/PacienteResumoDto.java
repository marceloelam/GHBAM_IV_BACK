package br.com.iv.resumoclinico.dto;
public record PacienteResumoDto(int pacienteId, String nome, String prontuario, String nascimento, int idade, String sexo, Integer bam, String unidade, String dataAtendimento) {}
