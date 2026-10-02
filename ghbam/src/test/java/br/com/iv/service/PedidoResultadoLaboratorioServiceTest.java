package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

// Os campos RNDS vem da tela e de sugestoes extraidas do PDF: vazio vira null
// (nao entra na fila RNDS) e texto longo e cortado no tamanho da coluna.
class PedidoResultadoLaboratorioServiceTest {

    @Test
    void textoVazioViraNulo() {
        assertNull(PedidoResultadoLaboratorioService.limitar(null, 30));
        assertNull(PedidoResultadoLaboratorioService.limitar("   ", 30));
    }

    @Test
    void cortaNoTamanhoDaColunaEApara() {
        assertEquals("2345-7", PedidoResultadoLaboratorioService.limitar(" 2345-7 ", 30));
        assertEquals("sangue ven", PedidoResultadoLaboratorioService.limitar("sangue venoso periferico", 10));
    }

    @Test
    void semLimiteParaColunasText() {
        String longo = "x".repeat(500);
        assertEquals(longo, PedidoResultadoLaboratorioService.limitar(longo, 0));
    }
}
