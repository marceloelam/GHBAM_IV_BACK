package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class VigilanciaEscalaMunicipalTest {

    private static final List<String> TRES = List.of("UPA A", "UPA B", "UBS C");

    private static String escala(long casos, long anterior, List<String> unidades) {
        return (String) VigilanciaEpidemiologicaController.escalaMunicipal("Dengue", casos, anterior, unidades).get("escala");
    }

    @Test
    void nomesDaMesmaDoencaTemAMesmaChave() {
        var chave = VigilanciaEpidemiologicaController.chaveDoenca("SRAG");
        assertEquals(chave, VigilanciaEpidemiologicaController.chaveDoenca("Síndrome Respiratória Aguda Grave"));
        assertEquals(chave, VigilanciaEpidemiologicaController.chaveDoenca("Síndrome Respiratória Aguda Grave (SRAG)"));
        assertNotEquals(chave, VigilanciaEpidemiologicaController.chaveDoenca("Síndrome Gripal"));
    }

    @Test
    void pocosCasosNaoGeramSinal() {
        assertEquals("NENHUMA", escala(4, 0, TRES));
    }

    @Test
    void dobroComCincoCasosEPossivelSurto() {
        assertEquals("POSSIVEL_SURTO", escala(10, 5, List.of("UPA A")));
        assertEquals("POSSIVEL_SURTO", escala(5, 2, List.of("UPA A")));
    }

    @Test
    void epidemiaExigeVolumeCrescimentoEVariasUnidades() {
        assertEquals("POSSIVEL_EPIDEMIA", escala(40, 15, TRES));
        // volume alto e crescimento, mas concentrado em duas unidades: segue como surto
        assertEquals("POSSIVEL_SURTO", escala(40, 15, List.of("UPA A", "UPA B")));
        // espalhado, mas sem crescimento de 2x
        assertEquals("ELEVACAO", escala(40, 25, TRES));
        // espalhado, crescendo, mas abaixo do volume de epidemia
        assertEquals("POSSIVEL_SURTO", escala(12, 5, TRES));
    }

    @Test
    void elevacaoModeradaNaoViraSurto() {
        assertEquals("ELEVACAO", escala(9, 6, List.of("UPA A")));
        assertEquals("NENHUMA", escala(9, 8, List.of("UPA A")));
    }

    @Test
    void semHistoricoValeSoOVolumeENaoHaVariacao() {
        var r = VigilanciaEpidemiologicaController.escalaMunicipal("Dengue", 25, 0, TRES);
        assertEquals("POSSIVEL_EPIDEMIA", r.get("escala"));
        assertNull(r.get("variacaoPct"));
        assertEquals(3, r.get("unidadesAfetadas"));
    }
}
