package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FluxoCirurgicoAssistencialTest {

    @Test
    void cirurgiaSempreGeraDemandaDeCmeEApenasQuandoIndicadaGeraHemoterapia() {
        var demandas = FluxoCirurgicoAssistencial.demandasParaCirurgia(42, "CIR-202609-00001", true);

        assertEquals(2, demandas.size());
        assertEquals("CME", demandas.get(0).modulo());
        assertEquals("HEMOTERAPIA", demandas.get(1).modulo());
    }

    @Test
    void cirurgiaSemHemocomponenteGeraSomenteDemandaDeCme() {
        var demandas = FluxoCirurgicoAssistencial.demandasParaCirurgia(42, "CIR-202609-00001", false);

        assertEquals(1, demandas.size());
        assertEquals("CME", demandas.get(0).modulo());
    }

    @Test
    void inicioDaCirurgiaExigeLiberacaoDeTodasAsDemandasAplicaveis() {
        assertEquals(false, FluxoCirurgicoAssistencial.podeIniciar("CONCLUIDO", "ABERTO", true));
        assertEquals(true, FluxoCirurgicoAssistencial.podeIniciar("CONCLUIDO", "CONCLUIDO", true));
        assertEquals(true, FluxoCirurgicoAssistencial.podeIniciar("CONCLUIDO", null, false));
    }
}
