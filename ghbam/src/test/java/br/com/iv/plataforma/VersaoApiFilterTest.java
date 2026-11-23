package br.com.iv.plataforma;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** /v1/... e' a mesma rota sem versao; o resto passa intacto. */
class VersaoApiFilterTest {

    @Test
    void tiraSoOPrefixoV1() {
        assertEquals("pacientes/10", VersaoApiFilter.semVersao("v1/pacientes/10"));
        assertEquals("pacientes/10", VersaoApiFilter.semVersao("/v1/pacientes/10"));
        assertEquals("", VersaoApiFilter.semVersao("v1"));
        assertEquals("", VersaoApiFilter.semVersao("v1/"));
    }

    @Test
    void naoMexeNoQueNaoEV1() {
        assertNull(VersaoApiFilter.semVersao("pacientes/10"));
        assertNull(VersaoApiFilter.semVersao("v10/pacientes"));
        assertNull(VersaoApiFilter.semVersao("v2/pacientes"));
        assertNull(VersaoApiFilter.semVersao("api/v1/x"));
        assertNull(VersaoApiFilter.semVersao(null));
    }
}
