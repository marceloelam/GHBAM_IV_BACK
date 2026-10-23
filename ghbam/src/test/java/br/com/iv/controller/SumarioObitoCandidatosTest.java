package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SumarioObitoCandidatosTest {

    private static final String SQL = SumarioObitoController.SQL_OBITOS;

    @Test
    void listaSoObitosDaUnidadeDoUsuario() {
        assertTrue(SQL.contains("JOIN bam b ON b.idbam = o.idbam AND b.unidade = :unidade"));
        assertTrue(SQL.contains("r.obito = true AND r.dataobito IS NOT NULL"));
        assertTrue(SQL.contains("t.tipo_alta ILIKE '%bito%'"));
        assertTrue(SQL.contains("i.motivodaalta ILIKE '%bito%'"));
    }

    @Test
    void resumoDeAltaCanceladoNaoContaComoObito() {
        assertTrue(SQL.contains("COALESCE(r.status, 'ATIVO') <> 'CANCELADO'"));
    }

    @Test
    void bamTextoSoViraNumeroQuandoForNumerico() {
        assertEquals(2, SQL.split("~ '\\^\\[0-9\\]\\{1,9\\}\\$'", -1).length - 1);
    }

    @Test
    void sumarioSalvoNaoCriaCandidatoSemObitoClinico() {
        String cte = SQL.substring(SQL.indexOf("WITH obitos AS ("), SQL.indexOf("SELECT b.idbam, p.nome"));
        assertTrue(!cte.contains("FROM sumarioobito"));
    }

    @Test
    void fontesDeAltaSemDataNaoCriamCandidatos() {
        assertTrue(SQL.contains("AND EXISTS (SELECT 1 FROM resumodealta r WHERE r.bam = CAST(b.idbam AS text) AND r.obito = true AND r.dataobito IS NOT NULL"));
        assertTrue(SQL.contains("AND EXISTS (SELECT 1 FROM resumodealta r WHERE r.bam = i.bam AND r.obito = true AND r.dataobito IS NOT NULL"));
    }

    @Test
    void consultaTemLimite() {
        assertTrue(SQL.trim().endsWith("LIMIT " + SumarioObitoController.LIMITE_OBITOS));
    }

    @Test
    void cancelamentoExigeMotivo() {
        assertNull(SumarioObitoController.motivoValido(null));
        assertNull(SumarioObitoController.motivoValido("   "));
        assertNull(SumarioObitoController.motivoValido("erro"));
        assertEquals("Paciente errado", SumarioObitoController.motivoValido("  Paciente errado  "));
    }
}
