package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CicloMedicacaoFilaServiceTest {

    private CicloMedicacaoFilaService.ItemCiclo item(boolean disp, boolean rec, boolean apz, boolean chk) {
        return new CicloMedicacaoFilaService.ItemCiclo(1, "DIPIRONA", null, disp, rec, apz, chk);
    }

    @Test
    void farmaciaVeSoONaoDispensado() {
        assertEquals("DISPENSACAO", CicloMedicacaoFilaService.etapaPendente(item(false, false, false, false), true, true));
        assertNull(CicloMedicacaoFilaService.etapaPendente(item(true, false, false, false), true, true));
    }

    @Test
    void enfermagemEsperaAFarmaciaQuandoAUnidadeDispensa() {
        assertEquals("AGUARDANDO_FARMACIA", CicloMedicacaoFilaService.etapaPendente(item(false, false, false, false), false, true));
        assertEquals("RECEBIMENTO_ENFERMAGEM", CicloMedicacaoFilaService.etapaPendente(item(true, false, false, false), false, true));
        assertEquals("APRAZAMENTO", CicloMedicacaoFilaService.etapaPendente(item(true, true, false, false), false, true));
        assertEquals("CHECAGEM", CicloMedicacaoFilaService.etapaPendente(item(true, true, true, false), false, true));
    }

    @Test
    void itemConcluidoSaiDaFila() {
        assertNull(CicloMedicacaoFilaService.etapaPendente(item(true, true, true, true), false, true));
    }

    @Test
    void unidadeSemDispensacaoLiberaEnfermagemEEsvaziaFarmacia() {
        assertEquals("RECEBIMENTO_ENFERMAGEM", CicloMedicacaoFilaService.etapaPendente(item(false, false, false, false), false, false));
        assertNull(CicloMedicacaoFilaService.etapaPendente(item(false, false, false, false), true, false));
    }

    @Test
    void recebidoDoEstoqueDoSetorSegueParaAEnfermagemEFarmaciaRepoe() {
        var i = new CicloMedicacaoFilaService.ItemCiclo(1, "DIPIRONA", null, false, true, false, false, false, true, true);
        assertEquals("APRAZAMENTO", CicloMedicacaoFilaService.etapaPendente(i, false, true));
        assertEquals("REPOR_ESTOQUE", CicloMedicacaoFilaService.etapaPendente(i, true, true));
    }

    @Test
    void trazidoPeloPacienteNaoGeraPendenciaParaAFarmacia() {
        var i = new CicloMedicacaoFilaService.ItemCiclo(1, "LOSARTANA", null, false, true, false, false, false, false, true);
        assertNull(CicloMedicacaoFilaService.etapaPendente(i, true, true));
        assertEquals("APRAZAMENTO", CicloMedicacaoFilaService.etapaPendente(i, false, true));
    }

    @Test
    void cuidadoNaoPassaPelaFarmaciaSoAprazaEChecaa() {
        var cuidado = new CicloMedicacaoFilaService.ItemCiclo(1, "CABECEIRA 30", null, false, false, false, false, false, false, false);
        assertNull(CicloMedicacaoFilaService.etapaPendente(cuidado, true, true));
        assertEquals("APRAZAMENTO", CicloMedicacaoFilaService.etapaPendente(cuidado, false, true));
        var aprazado = new CicloMedicacaoFilaService.ItemCiclo(1, "CABECEIRA 30", null, false, false, true, false, false, false, false);
        assertEquals("CHECAGEM", CicloMedicacaoFilaService.etapaPendente(aprazado, false, true));
    }

    @Test
    void dispensadoEmPapelVoltaParaAFarmaciaRegularizar() {
        var papel = new CicloMedicacaoFilaService.ItemCiclo(1, "DIPIRONA", null, false, true, false, false, true);
        assertEquals("REGULARIZAR_PAPEL", CicloMedicacaoFilaService.etapaPendente(papel, true, true));
        assertEquals("APRAZAMENTO", CicloMedicacaoFilaService.etapaPendente(papel, false, true));
        var lancado = new CicloMedicacaoFilaService.ItemCiclo(1, "DIPIRONA", null, true, true, false, false, true);
        assertNull(CicloMedicacaoFilaService.etapaPendente(lancado, true, true));
    }
}
