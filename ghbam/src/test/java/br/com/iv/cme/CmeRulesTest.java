package br.com.iv.cme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CmeRulesTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 30);

    @Test
    void liberaCicloComFisicoEQuimicoConformes() {
        assertNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "CONFORME", "CONFORME", "NAO_REALIZADO", false, 3));
    }

    @Test
    void naoLiberaSemPacoteOuComIndicadorPendente() {
        assertNotNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "CONFORME", "CONFORME", "NAO_REALIZADO", false, 0));
        assertNotNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "PENDENTE", "CONFORME", "NAO_REALIZADO", false, 1));
        assertNotNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "CONFORME", "PENDENTE", "NAO_REALIZADO", false, 1));
        assertNotNull(CmeRules.bloqueioLiberacao("LIBERADO", "CONFORME", "CONFORME", "NAO_REALIZADO", false, 1));
    }

    @Test
    void implanteExigeBiologicoNegativo() {
        assertNotNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "CONFORME", "CONFORME", "PENDENTE", true, 1));
        assertNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "CONFORME", "CONFORME", "NEGATIVO", true, 1));
    }

    @Test
    void qualquerFalhaDeIndicadorExigeReprovacao() {
        assertTrue(CmeRules.exigeReprovacao("NAO_CONFORME", "CONFORME", "NEGATIVO"));
        assertTrue(CmeRules.exigeReprovacao("CONFORME", "NAO_CONFORME", "NEGATIVO"));
        assertTrue(CmeRules.exigeReprovacao("CONFORME", "CONFORME", "POSITIVO"));
        assertFalse(CmeRules.exigeReprovacao("CONFORME", "CONFORME", "PENDENTE"));
        assertNotNull(CmeRules.bloqueioLiberacao("EM_ANALISE", "CONFORME", "CONFORME", "POSITIVO", false, 1));
    }

    @Test
    void saidaExigeCicloLiberadoEValidade() {
        assertNull(CmeRules.bloqueioSaida("LIBERADO", "ESTERILIZADO", HOJE, HOJE, false));
        assertNotNull(CmeRules.bloqueioSaida("EM_ANALISE", "ESTERILIZADO", HOJE.plusDays(5), HOJE, false));
        assertNotNull(CmeRules.bloqueioSaida("REPROVADO", "ESTERILIZADO", HOJE.plusDays(5), HOJE, true));
        assertNotNull(CmeRules.bloqueioSaida("LIBERADO", "ESTERILIZADO", HOJE.minusDays(1), HOJE, true));
    }

    @Test
    void distribuidoPodeSerUsadoMasNaoRedistribuido() {
        assertNull(CmeRules.bloqueioSaida("LIBERADO", "DISTRIBUIDO", HOJE, HOJE, true));
        assertNotNull(CmeRules.bloqueioSaida("LIBERADO", "DISTRIBUIDO", HOJE, HOJE, false));
        assertNotNull(CmeRules.bloqueioSaida("LIBERADO", "UTILIZADO", HOJE, HOJE, true));
        assertNotNull(CmeRules.bloqueioSaida("LIBERADO", "RECOLHIDO", HOJE, HOJE, true));
    }

    @Test
    void recolhimentoNaoAlcancaPacoteJaUsado() {
        assertTrue(CmeRules.recolhivel("ESTERILIZADO"));
        assertTrue(CmeRules.recolhivel("DISTRIBUIDO"));
        assertFalse(CmeRules.recolhivel("UTILIZADO"));
        assertFalse(CmeRules.podeBaixar("UTILIZADO"));
        assertFalse(CmeRules.podeBaixar("BAIXADO"));
        assertTrue(CmeRules.podeBaixar("RECOLHIDO"));
    }

    @Test
    void preparoSegueRecebidoLimpoPronto() {
        assertNull(CmeRules.bloqueioEtapaPreparo("RECEBIDO", "LIMPAR"));
        assertNull(CmeRules.bloqueioEtapaPreparo("LIMPO", "PREPARAR"));
        assertNull(CmeRules.bloqueioEtapaPreparo("PRONTO", "CARGA"));
        assertNotNull(CmeRules.bloqueioEtapaPreparo("RECEBIDO", "PREPARAR"));
        assertNotNull(CmeRules.bloqueioEtapaPreparo("LIMPO", "CARGA"));
        assertNotNull(CmeRules.bloqueioEtapaPreparo("NA_CARGA", "CARGA"));
        assertEquals("TERMODESINFECCAO", CmeRules.metodoLimpeza("termodesinfeccao"));
        assertThrows(IllegalArgumentException.class, () -> CmeRules.embalagem("PAPEL"));
    }

    @Test
    void cancelaSoCicloEmAnalise() {
        assertNull(CmeRules.bloqueioCancelamento("EM_ANALISE"));
        assertNotNull(CmeRules.bloqueioCancelamento("LIBERADO"));
        assertNotNull(CmeRules.bloqueioCancelamento("REPROVADO"));
        assertNotNull(CmeRules.bloqueioCancelamento("CANCELADO"));
        assertNotNull(CmeRules.bloqueioSaida("CANCELADO", "ESTERILIZADO", HOJE, HOJE, true));
    }

    @Test
    void numeraCicloEmSequenciaPorEquipamento() {
        assertEquals("1", CmeRules.proximoNumeroCiclo(java.util.List.of()));
        assertEquals("13", CmeRules.proximoNumeroCiclo(java.util.List.of("3", "12", "T-001", "007")));
    }

    @Test
    void reprocessaRecolhidoOuVencidoNoArsenal() {
        assertTrue(CmeRules.reprocessavel("RECOLHIDO", HOJE.plusDays(10), HOJE));
        assertTrue(CmeRules.reprocessavel("ESTERILIZADO", HOJE.minusDays(1), HOJE));
        assertFalse(CmeRules.reprocessavel("ESTERILIZADO", HOJE.plusDays(1), HOJE));
        assertFalse(CmeRules.reprocessavel("UTILIZADO", HOJE.minusDays(1), HOJE));
        assertFalse(CmeRules.reprocessavel("BAIXADO", HOJE.minusDays(1), HOJE));
    }

    @Test
    void validaDominios() {
        assertEquals("VAPOR", CmeRules.metodo(" vapor "));
        assertThrows(IllegalArgumentException.class, () -> CmeRules.metodo("FORNO"));
        assertEquals("PENDENTE", CmeRules.indicador(null));
        assertThrows(IllegalArgumentException.class, () -> CmeRules.indicador("OK"));
        assertEquals("NAO_REALIZADO", CmeRules.biologico(""));
        assertEquals("CME-HMSFX-0000042", CmeRules.codigoPacote("hmsfx", 42));
    }
}
