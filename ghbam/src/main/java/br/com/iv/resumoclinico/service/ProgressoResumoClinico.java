package br.com.iv.resumoclinico.service;

@FunctionalInterface
public interface ProgressoResumoClinico {
    void atualizar(String fase, String mensagem, int percentual);
}
