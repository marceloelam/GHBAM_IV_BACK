package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RegrasSurtoEpidemiologicoTest {

    private static final String INV = RegrasSurtoEpidemiologico.EM_INVESTIGACAO;
    private static final String CONF = RegrasSurtoEpidemiologico.CONFIRMADO;
    private static final String DESC = RegrasSurtoEpidemiologico.DESCARTADO;
    private static final String ENC = RegrasSurtoEpidemiologico.ENCERRADO;

    @Test
    void declararExigeDoisConfirmados() {
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(INV, CONF, "vinculo em escola", 1, false));
        assertNull(RegrasSurtoEpidemiologico.validarMudanca(INV, CONF, "vinculo em escola", 2, false));
    }

    @Test
    void agravoRaroBastaUmConfirmado() {
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(INV, CONF, "caso de sarampo", 0, true));
        assertNull(RegrasSurtoEpidemiologico.validarMudanca(INV, CONF, "caso de sarampo", 1, true));
    }

    @Test
    void justificativaEObrigatoria() {
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(INV, DESC, " ", 0, false));
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(CONF, ENC, null, 5, false));
    }

    @Test
    void transicoes() {
        assertNull(RegrasSurtoEpidemiologico.validarMudanca(INV, DESC, "sem vinculo", 0, false));
        assertNull(RegrasSurtoEpidemiologico.validarMudanca(CONF, ENC, "sem casos novos", 5, false));
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(INV, ENC, "x", 5, false));
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(CONF, DESC, "x", 5, false));
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(ENC, CONF, "x", 5, false));
        assertNotNull(RegrasSurtoEpidemiologico.validarMudanca(DESC, INV, "x", 5, false));
    }

    @Test
    void vinculo() {
        assertNull(RegrasSurtoEpidemiologico.validarVinculo("SUSPEITO", INV));
        assertNull(RegrasSurtoEpidemiologico.validarVinculo(null, CONF));
        assertNotNull(RegrasSurtoEpidemiologico.validarVinculo("DESCARTADO", INV));
        assertNotNull(RegrasSurtoEpidemiologico.validarVinculo("CONFIRMADO", ENC));
    }
}
