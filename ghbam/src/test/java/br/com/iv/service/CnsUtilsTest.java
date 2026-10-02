package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CnsUtilsTest {

    // Definitivos (familia 1/2) gerados pela propria regra do PIS/PASEP: se o calculo
    // do digito mudar, estes valores param de fechar.
    @Test
    void aceitaCnsDefinitivo() {
        assertTrue(CnsUtils.valido("123456789010000"));
        assertTrue(CnsUtils.valido("200000000010009"));
        assertTrue(CnsUtils.valido("198765432100003"));
    }

    // Provisorios (familia 7/8/9), o caso do recem-nascido cadastrado pela DNV: a soma
    // ponderada dos 15 digitos tem de fechar em multiplo de 11.
    @Test
    void aceitaCnsProvisorio() {
        assertTrue(CnsUtils.valido("700000000000005"));
        assertTrue(CnsUtils.valido("700000000000013"));
    }

    @Test
    void aceitaCnsComMascara() {
        assertTrue(CnsUtils.valido("700 0000 0000 0005"));
    }

    // O caso que motivou a regra: numero com 15 digitos que passava por valido so
    // porque tinha o tamanho certo, e voltava rejeitado depois do envio.
    @Test
    void recusaDigitoVerificadorErrado() {
        assertFalse(CnsUtils.valido("123456789010001"));
        assertFalse(CnsUtils.valido("700000000000006"));
    }

    @Test
    void recusaTamanhoErrado() {
        assertFalse(CnsUtils.valido("70000000000000"));
        assertFalse(CnsUtils.valido("7000000000000055"));
        assertFalse(CnsUtils.valido(""));
        assertFalse(CnsUtils.valido(null));
    }

    @Test
    void recusaFamiliaInexistente() {
        assertFalse(CnsUtils.valido("300000000000000"));
        assertFalse(CnsUtils.valido("000000000000000"));
    }

    // Sequencia repetida chega a fechar a soma, mas nunca e um CNS real - e o valor
    // tipico de campo preenchido so para passar da critica.
    @Test
    void recusaDigitosRepetidos() {
        assertFalse(CnsUtils.valido("111111111111111"));
        assertFalse(CnsUtils.valido("999999999999999"));
    }

    @Test
    void explicaOMotivoDaRecusa() {
        assertEquals("CNS nao informado.", CnsUtils.motivoInvalido(""));
        assertEquals("CNS tem 15 digitos e foram informados 14.", CnsUtils.motivoInvalido("70000000000000"));
        assertEquals("CNS comeca com 1, 2 (definitivo) ou 7, 8, 9 (provisorio).",
                CnsUtils.motivoInvalido("300000000000000"));
        assertEquals("Digito verificador do CNS nao confere - confira o numero no CADSUS.",
                CnsUtils.motivoInvalido("700000000000006"));
    }
}
