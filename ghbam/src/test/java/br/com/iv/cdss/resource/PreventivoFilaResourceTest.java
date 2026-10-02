package br.com.iv.cdss.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// A lista preventiva sem busca gerava uma linha de log a cada ~1,5 min por profissional.
class PreventivoFilaResourceTest {

    private static final long MINUTO = 60_000L;

    @Test
    void listaSemBuscaGravaNoMaximoUmaVezACada10Minutos() {
        PreventivoFilaResource resource = new PreventivoFilaResource();
        assertTrue(resource.deveAuditarLista("11111|UNID", false, 0));
        assertFalse(resource.deveAuditarLista("11111|UNID", false, 2 * MINUTO));
        assertFalse(resource.deveAuditarLista("11111|UNID", false, 9 * MINUTO));
        assertTrue(resource.deveAuditarLista("11111|UNID", false, 10 * MINUTO));
    }

    @Test
    void outroProfissionalEBuscaSempreGravam() {
        PreventivoFilaResource resource = new PreventivoFilaResource();
        assertTrue(resource.deveAuditarLista("11111|UNID", false, 0));
        assertTrue(resource.deveAuditarLista("22222|UNID", false, MINUTO));
        assertTrue(resource.deveAuditarLista("11111|UNID", true, MINUTO));
        assertTrue(resource.deveAuditarLista("11111|UNID", true, 2 * MINUTO));
    }
}
