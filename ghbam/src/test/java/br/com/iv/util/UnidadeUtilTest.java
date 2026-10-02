package br.com.iv.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UnidadeUtilTest {

    @Test
    void normalizaSiglaComDescricao() {
        assertEquals("HMSFX", UnidadeUtil.normalizar(" hmsfx: Hospital Municipal São Francisco Xavier "));
    }

    @Test
    void preservaSomenteASiglaNormalizada() {
        assertEquals("HMSFX", UnidadeUtil.normalizar("Hmsfx"));
    }

    @Test
    void trataValorAusente() {
        assertEquals("", UnidadeUtil.normalizar(null));
        assertEquals("", UnidadeUtil.normalizar("   "));
    }
}
