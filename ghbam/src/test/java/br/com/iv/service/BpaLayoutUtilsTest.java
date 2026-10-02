package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Formatacao dos campos do arquivo BPA. O caso que originou os testes: paciente
// cadastrado na recepcao com nascimento "1984/07/23" saia no BPA-I como "00000000".
class BpaLayoutUtilsTest {

    // Formato que o cadastro de pacientes usa e que DateUtils.toYMD produz.
    @Test
    void leNascimentoNoFormatoDoCadastroComBarras() {
        assertEquals("19840723", BpaLayoutUtils.data8("1984/07/23"));
    }

    @Test
    void continuaLendoOsFormatosQueJaFuncionavam() {
        assertEquals("20260827", BpaLayoutUtils.data8("2026-08-27"));
        assertEquals("19840723", BpaLayoutUtils.data8("23/07/1984"));
        assertEquals("19840723", BpaLayoutUtils.data8("19840723"));
    }

    // Data ausente ou ilegivel continua zerada - melhor um campo obviamente vazio do que
    // uma data inventada.
    @Test
    void dataDesconhecidaFicaZerada() {
        assertEquals("00000000", BpaLayoutUtils.data8(""));
        assertEquals("00000000", BpaLayoutUtils.data8(null));
        assertEquals("00000000", BpaLayoutUtils.data8("nao informado"));
    }

    // As telas mandam cid = '0' quando nao ha diagnostico; isso virava "0000" no arquivo.
    @Test
    void cidZeradoViraBranco() {
        assertEquals("", BpaLayoutUtils.cid("0"));
        assertEquals("", BpaLayoutUtils.cid("0000"));
        assertEquals("", BpaLayoutUtils.cid(""));
        assertEquals("", BpaLayoutUtils.cid(null));
    }

    @Test
    void cidValidoEPreservado() {
        assertEquals("A090", BpaLayoutUtils.cid("a090"));
        assertEquals("I10", BpaLayoutUtils.cid(" I10 "));
    }
}
