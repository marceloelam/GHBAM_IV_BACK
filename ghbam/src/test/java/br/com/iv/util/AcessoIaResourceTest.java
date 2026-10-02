package br.com.iv.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AcessoIaResourceTest {

    @Test
    void deveDeixarBackendEscolherProvedorQuandoModeloNaoFoiInformado() {
        assertNull(AcessoIaResource.modeloSolicitado(Map.of("pergunta", "teste")));
    }

    @Test
    void deveRespeitarModeloExplicitamenteInformado() {
        assertEquals("groq:modelo", AcessoIaResource.modeloSolicitado(
            Map.of("pergunta", "teste", "modelo", "groq:modelo")
        ));
    }
}
