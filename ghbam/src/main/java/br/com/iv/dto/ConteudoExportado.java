package br.com.iv.dto;

import java.util.List;

/**
 * pendencias: registros que ficaram fora do arquivo, com o motivo, para o faturista
 * resolver sem perder o restante da competencia. Vazio nos exportadores que ainda
 * recusam o arquivo inteiro.
 */
public record ConteudoExportado(byte[] conteudo, int quantidade, String layout, List<String> pendencias) {

    public ConteudoExportado(byte[] conteudo, int quantidade, String layout) {
        this(conteudo, quantidade, layout, List.of());
    }

    public ConteudoExportado {
        pendencias = pendencias == null ? List.of() : List.copyOf(pendencias);
    }
}
