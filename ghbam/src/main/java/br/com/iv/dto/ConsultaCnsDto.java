package br.com.iv.dto;

import java.util.List;

// Retorno da consulta ao cadastro publico do CNES (CPF + data de nascimento -> CNS).
// "encontrado" separa os dois nao-resultados que o DATASUS mistura numa pagina so:
// nao achou o CPF (mensagens preenchidas) e servico fora do ar (excecao no service).
public record ConsultaCnsDto(
        boolean encontrado,
        String cns,
        String nome,
        String cpf,
        String dataNascimento,
        List<String> mensagens) {

    public static ConsultaCnsDto naoEncontrado(List<String> mensagens) {
        return new ConsultaCnsDto(false, "", "", "", "", mensagens == null ? List.of() : mensagens);
    }
}
