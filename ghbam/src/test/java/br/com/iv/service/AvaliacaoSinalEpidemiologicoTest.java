package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.iv.service.AvaliacaoSinalEpidemiologico.Entrada;
import br.com.iv.service.AvaliacaoSinalEpidemiologico.Estado;
import br.com.iv.service.AvaliacaoSinalEpidemiologico.Resultado;

class AvaliacaoSinalEpidemiologicoTest {

    private static Resultado avaliar(long atual, double media, Double epoca, boolean aberta) {
        return AvaliacaoSinalEpidemiologico.avaliar(new Entrada(atual, media, epoca, 1d, 1d, 3, 2d, false, aberta, false));
    }

    /** Antes o alerta virava "normal" na semana 6, com 100 casos por semana. */
    @Test
    void epidemiaSustentadaNaoApagaNoPico() {
        int[] casos = { 0, 0, 0, 0, 10, 30, 60, 90, 100, 100, 100, 100, 100, 100 };
        boolean aberta = false;
        for (int i = 4; i < casos.length; i++) {
            double media = (casos[i - 1] + casos[i - 2] + casos[i - 3] + casos[i - 4]) / 4d;
            Resultado r = avaliar(casos[i], media, null, aberta);
            assertTrue(r.alerta(), "semana " + (i - 3));
            aberta = true;
        }
    }

    @Test
    void platoSemOcorrenciaAbertaFicaNormalSemHistoricoDeEpoca() {
        assertEquals(Estado.NORMAL, avaliar(100, 100, null, false).estado());
    }

    @Test
    void platoAcimaDaEpocaAlertaMesmoSemOcorrencia() {
        Resultado r = avaliar(100, 100d, 10d, false);
        assertEquals(Estado.ACIMA_DA_EPOCA, r.estado());
        assertTrue(r.registrarOcorrencia());
    }

    @Test
    void emCursoNaoRegravaOcorrencia() {
        Resultado r = avaliar(100, 100d, null, true);
        assertEquals(Estado.EM_CURSO, r.estado());
        assertTrue(r.alerta());
        assertFalse(r.registrarOcorrencia());
    }

    @Test
    void altaCompativelComAEpocaContinuaAlertandoMasFicaMarcada() {
        Resultado r = avaliar(50, 10d, 40d, false);
        assertEquals(Estado.SUBINDO, r.estado());
        assertTrue(r.alerta());
        assertTrue(r.compativelComEpoca());
    }

    @Test
    void tresCasosComMediaUmNaoDisparamPorAcaso() {
        assertEquals(Estado.NORMAL, avaliar(3, 1d, null, false).estado());
        assertEquals(Estado.SUBINDO, avaliar(4, 1d, null, false).estado());
    }

    @Test
    void semHistoricoValeOMinimo() {
        assertEquals(Estado.NORMAL, avaliar(2, 0d, null, false).estado());
        assertEquals(Estado.SUBINDO, avaliar(3, 0d, null, false).estado());
    }

    @Test
    void abaixoDoMinimoENormalMesmoComOcorrenciaAberta() {
        assertEquals(Estado.NORMAL, avaliar(2, 10d, null, true).estado());
    }

    @Test
    void casoRaroAlertaComUmCasoMesmoJaRevisado() {
        Resultado r = AvaliacaoSinalEpidemiologico.avaliar(new Entrada(1, 2d, 2d, 1d, 1d, 1, 2d, true, false, true));
        assertEquals(Estado.CASO_RARO, r.estado());
        assertTrue(r.alerta());
    }

    @Test
    void sinalEncerradoNaJanelaNaoReabreSozinho() {
        Resultado r = AvaliacaoSinalEpidemiologico.avaliar(new Entrada(30, 5d, null, 1d, 1d, 3, 2d, false, false, true));
        assertEquals(Estado.REVISADO, r.estado());
        assertFalse(r.alerta());
        assertFalse(r.registrarOcorrencia());
    }

    @Test
    void movimentoMaiorAumentaOEsperado() {
        Entrada semAjuste = new Entrada(20, 10d, null, 1d, 1d, 3, 2d, false, false, false);
        Entrada movimentoDobrado = new Entrada(20, 10d, null, 2d, 1d, 3, 2d, false, false, false);
        assertEquals(Estado.SUBINDO, AvaliacaoSinalEpidemiologico.avaliar(semAjuste).estado());
        assertEquals(Estado.NORMAL, AvaliacaoSinalEpidemiologico.avaliar(movimentoDobrado).estado());
    }

    @Test
    void ajusteFicaEntreUmEDois() {
        assertEquals(1d, AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(50, 100));
        assertEquals(1.5d, AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(150, 100));
        assertEquals(2d, AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(900, 100));
        assertEquals(1d, AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(900, 0));
    }

    @Test
    void probabilidadeAoAcasoDePoisson() {
        assertEquals(0.0803, AvaliacaoSinalEpidemiologico.probabilidadeAoAcaso(3, 1d), 0.0001);
        assertEquals(0.019, AvaliacaoSinalEpidemiologico.probabilidadeAoAcaso(4, 1d), 0.0001);
        assertEquals(0d, AvaliacaoSinalEpidemiologico.probabilidadeAoAcaso(400, 100d));
    }
}
