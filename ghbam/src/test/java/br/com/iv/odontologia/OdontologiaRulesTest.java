package br.com.iv.odontologia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OdontologiaRulesTest {
    @Test
    void deveGerarNumeroVitalicioComAnoDeCriacao() {
        assertEquals("2026-000123", OdontologiaRules.numeroProntuario(2026, 123));
    }

    @Test
    void reconheceNumeracaoFdiPermanenteEDecidua() {
        assertTrue(OdontologiaRules.numeroFdiValido(11));
        assertTrue(OdontologiaRules.numeroFdiValido(48));
        assertTrue(OdontologiaRules.numeroFdiValido(51));
        assertTrue(OdontologiaRules.numeroFdiValido(85));
        assertFalse(OdontologiaRules.numeroFdiValido(19));
        assertFalse(OdontologiaRules.numeroFdiValido(99));
    }

    @Test
    void bloqueiaEdicaoDepoisDaAssinatura() {
        assertThrows(IllegalStateException.class, () -> OdontologiaRules.exigirEditavel("ASSINADO"));
        assertThrows(IllegalStateException.class, () -> OdontologiaRules.exigirEditavel("ADENDO"));
        OdontologiaRules.exigirEditavel("RASCUNHO");
    }

    @Test
    void validaFacesECondicoesClinicas() {
        assertTrue(OdontologiaRules.faceValida("OCLUSAL"));
        assertTrue(OdontologiaRules.faceValida("DENTE_INTEIRO"));
        assertFalse(OdontologiaRules.faceValida("SUPERIOR"));
        assertTrue(OdontologiaRules.condicaoValida("CARIE"));
        assertFalse(OdontologiaRules.condicaoValida("QUALQUER"));
    }
}
