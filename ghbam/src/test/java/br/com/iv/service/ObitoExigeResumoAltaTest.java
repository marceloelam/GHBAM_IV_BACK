package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObitoExigeResumoAltaTest {

    @Test
    void saidaPorObitoExigeResumoPreenchido() {
        assertTrue(NovoInternadoService.exigeResumoObito("OBITO"));
        assertTrue(NovoInternadoService.exigeResumoObito("OBITO INTERNADOS"));
        assertTrue(NovoInternadoService.exigeResumoObito("Óbito BAM"));
    }

    @Test
    void naoObitoENatimortoNaoEntramNaTrava() {
        assertFalse(NovoInternadoService.exigeResumoObito("OBITO NATIMORTO"));
        assertFalse(NovoInternadoService.exigeResumoObito("ALTA MELHORADO"));
        assertFalse(NovoInternadoService.exigeResumoObito(null));
    }
}
