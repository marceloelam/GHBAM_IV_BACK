package br.com.iv.almoxarifado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlmoxRulesTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void custoMedioPonderado() {
        // 10 un a 2,00 + 10 un a 4,00 = 3,00
        assertEquals(0, n("3.0000").compareTo(AlmoxRules.custoMedio(n("10"), n("2"), n("10"), n("4"))));
        // estoque zerado: vale o custo da entrada
        assertEquals(0, n("5.5000").compareTo(AlmoxRules.custoMedio(n("0"), n("2"), n("4"), n("5.5"))));
    }

    @Test
    void pontoDePedido() {
        assertTrue(AlmoxRules.abaixoDoMinimo(n("5"), n("5")));
        assertFalse(AlmoxRules.abaixoDoMinimo(n("6"), n("5")));
        assertFalse(AlmoxRules.abaixoDoMinimo(n("0"), n("0")));
    }

    @Test
    void situacaoDoPedido() {
        assertEquals("ATENDIDA", AlmoxRules.statusAtendimento(List.<BigDecimal[]>of(new BigDecimal[] {n("2"), n("2")})));
        assertEquals("PARCIAL", AlmoxRules.statusAtendimento(List.of(new BigDecimal[] {n("2"), n("1")}, new BigDecimal[] {n("3"), n("0")})));
        assertEquals("RECUSADA", AlmoxRules.statusAtendimento(List.<BigDecimal[]>of(new BigDecimal[] {n("2"), n("0")})));
    }

    @Test
    void curvaAbc() {
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        valores.put(1L, n("700"));
        valores.put(2L, n("200"));
        valores.put(3L, n("60"));
        valores.put(4L, n("40"));
        valores.put(5L, n("0"));
        Map<Long, String> abc = AlmoxRules.curvaAbc(valores);
        assertEquals("A", abc.get(1L));
        assertEquals("A", abc.get(2L));
        assertEquals("B", abc.get(3L));
        assertEquals("C", abc.get(4L));
        assertEquals("C", abc.get(5L));
    }

    @Test
    void sugestaoDeCompra() {
        assertEquals(0, n("70").compareTo(AlmoxRules.quantidadeSugerida(n("30"), n("50"), n("100"))));
        assertEquals(0, n("67").compareTo(AlmoxRules.quantidadeSugerida(n("33"), n("50"), null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(AlmoxRules.quantidadeSugerida(n("200"), n("50"), n("100"))));
    }

    @Test
    void transicoesDoPatrimonio() {
        assertEquals(null, AlmoxRules.bloqueioBem("EM_USO", "TRANSFERIR"));
        assertEquals(null, AlmoxRules.bloqueioBem("EM_USO", "MANUTENCAO"));
        assertEquals(null, AlmoxRules.bloqueioBem("MANUTENCAO", "RETORNO"));
        assertTrue(AlmoxRules.bloqueioBem("MANUTENCAO", "TRANSFERIR") != null);
        assertTrue(AlmoxRules.bloqueioBem("EM_USO", "RETORNO") != null);
        assertTrue(AlmoxRules.bloqueioBem("BAIXADO", "BAIXA") != null);
        assertThrows(IllegalArgumentException.class, () -> AlmoxRules.categoriaBem("NAVE"));
        // Acao inexistente e erro de requisicao mesmo com o bem baixado.
        assertThrows(IllegalArgumentException.class, () -> AlmoxRules.bloqueioBem("BAIXADO", "XPTO"));
    }

    @Test
    void cnpj() {
        assertTrue(AlmoxRules.cnpjValido("11.222.333/0001-81"));
        assertFalse(AlmoxRules.cnpjValido("11.222.333/0001-80"));
        assertFalse(AlmoxRules.cnpjValido("11111111111111"));
        assertFalse(AlmoxRules.cnpjValido("123"));
        // Alfanumerico (exemplo da Receita Federal).
        assertTrue(AlmoxRules.cnpjValido("12.ABC.345/01DE-35"));
        assertFalse(AlmoxRules.cnpjValido("12.ABC.345/01DE-36"));
        assertEquals("12ABC34501DE35", AlmoxRules.cnpjLimpo("12.abc.345/01de-35"));
    }

    @Test
    void compraRecebivel() {
        assertTrue(AlmoxRules.compraRecebivel("ENVIADO"));
        assertTrue(AlmoxRules.compraRecebivel("PARCIAL"));
        assertFalse(AlmoxRules.compraRecebivel("RECEBIDO"));
        assertFalse(AlmoxRules.compraRecebivel("ENCERRADO"));
    }

    @Test
    void grupos() {
        assertEquals("CONSUMO", AlmoxRules.grupo(null));
        assertEquals("LIMPEZA", AlmoxRules.grupo(" limpeza "));
        assertThrows(IllegalArgumentException.class, () -> AlmoxRules.grupo("ARMAS"));
    }
}
