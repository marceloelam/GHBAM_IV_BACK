package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LeitoOcupacaoGuardTest {

    private static final String MAE = "MARIA DA SILVA";

    private static Object[] ocupante(int bam, String mae, String nascimento) {
        return new Object[] { bam, mae, nascimento };
    }

    @Test
    void gemeosNascidosNoMesmoDiaDividemLeito() {
        assertTrue(LeitoOcupacaoGuard.irmaos(MAE, "2026/11/03", MAE, "2026/11/03"));
    }

    @Test
    void partoQueAtravessaAMeiaNoiteContaComoIrmaos() {
        assertTrue(LeitoOcupacaoGuard.irmaos(MAE, "2026/11/03", MAE, "2026/11/04"));
    }

    @Test
    void sextuplosEntramUmAUmSemConfirmacao() {
        List<Object[]> noLeito = new ArrayList<>();
        for (int bebe = 1; bebe <= 6; bebe++) {
            assertTrue(LeitoOcupacaoGuard.todosIrmaos(MAE, "2026/11/03", noLeito));
            noLeito.add(ocupante(bebe, MAE, "2026/11/03"));
        }
    }

    @Test
    void umOcupanteQueNaoEIrmaoExigeConfirmacao() {
        List<Object[]> noLeito = List.of(
                ocupante(1, MAE, "2026/11/03"),
                ocupante(2, "ANA SOUZA", "2026/11/03"));
        assertFalse(LeitoOcupacaoGuard.todosIrmaos(MAE, "2026/11/03", noLeito));
    }

    @Test
    void diferencaDeCaixaEEspacosNaoSeparaIrmaos() {
        assertTrue(LeitoOcupacaoGuard.irmaos(" Maria  da Silva ", "2026/11/03", MAE, "03-11-2026"));
    }

    @Test
    void mesmaMaeComNascimentoDistanteNaoEGemeo() {
        assertFalse(LeitoOcupacaoGuard.irmaos(MAE, "2026/11/03", MAE, "2024/02/10"));
    }

    @Test
    void maesDiferentesNaoSaoIrmaos() {
        assertFalse(LeitoOcupacaoGuard.irmaos(MAE, "2026/11/03", "ANA SOUZA", "2026/11/03"));
    }

    @Test
    void semMaeOuDataLegivelNaoPresumeIrmaos() {
        assertFalse(LeitoOcupacaoGuard.irmaos("", "2026/11/03", "", "2026/11/03"));
        assertFalse(LeitoOcupacaoGuard.irmaos(null, "2026/11/03", null, "2026/11/03"));
        assertFalse(LeitoOcupacaoGuard.irmaos(MAE, "ignorado", MAE, "2026/11/03"));
    }
}
