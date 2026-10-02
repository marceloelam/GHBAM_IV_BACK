package br.com.iv.dto;

import java.util.List;
import java.util.Map;

// Retorno da consulta oficial ao PDQ CADSUS/CNS (POST /cadsus/v2/PDQSupplierJWT).
//
// O manual do DATASUS (v2.1) documenta em detalhe o GET de token, mas nao traz o
// schema completo do corpo de resposta da consulta de pacientes - so os parametros
// de entrada (CNS, CPF, Nome, Nome da mae, NIS) e que a saida e uma "lista de
// resultados encontrados". Por isso guardamos sempre o corpoBruto: se o parsing
// para "dados" nao capturar tudo que a tela precisa, o corpo cru ja chega para
// diagnostico/ajuste, sem exigir nova chamada ao DATASUS.
public record CadsusPdqRespostaDto(
        boolean sucesso,
        int statusHttp,
        String mensagem,
        String corpoBruto,
        List<Map<String, Object>> pacientes) {

    public static CadsusPdqRespostaDto falha(String mensagem) {
        return new CadsusPdqRespostaDto(false, 0, mensagem, null, List.of());
    }

    public static CadsusPdqRespostaDto falhaHttp(int statusHttp, String mensagem, String corpoBruto) {
        return new CadsusPdqRespostaDto(false, statusHttp, mensagem, corpoBruto, List.of());
    }
}
