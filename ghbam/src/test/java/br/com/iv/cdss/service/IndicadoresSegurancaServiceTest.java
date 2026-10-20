package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.RoutePermissionCatalog;

class IndicadoresSegurancaServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 19);

    @Test
    void checagemDentroDaToleranciaEstaNoHorario() {
        LocalDateTime aprazado = LocalDateTime.of(2026, 10, 19, 7, 0);
        assertEquals("NO_HORARIO", IndicadoresSegurancaService.classificarChecagem(aprazado, "14:00", LocalDateTime.of(2026, 10, 19, 14, 25)));
        assertEquals("ATRASADA", IndicadoresSegurancaService.classificarChecagem(aprazado, "14:00", LocalDateTime.of(2026, 10, 19, 14, 45)));
        assertEquals("SEM_REFERENCIA", IndicadoresSegurancaService.classificarChecagem(aprazado, "--", LocalDateTime.of(2026, 10, 19, 14, 45)));
    }

    @Test
    void pacienteDiaNaoContaODiaDaAlta() {
        // Internou dia 10, alta dia 13: dias 10, 11 e 12.
        assertEquals(3, IndicadoresSegurancaService.diasInternado(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), HOJE));
        // Ainda internado desde setembro: outubro de 1 a 19.
        assertEquals(19, IndicadoresSegurancaService.diasInternado(LocalDate.of(2026, 9, 20), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), HOJE));
        // Alta no mesmo dia da internacao: zero paciente-dia.
        assertEquals(0, IndicadoresSegurancaService.diasInternado(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), HOJE));
    }

    @Test
    void validaSessaoEPeriodo() {
        IndicadoresSegurancaService service = new IndicadoresSegurancaService();
        assertThrows(SecurityException.class, () -> service.montar("2026-10-01", "2026-10-31", null));
        AuthenticatedUser nsp = new AuthenticatedUser("1", "555", "NSP", null, "HMSFX", null, null, null, null,
                null, null, null, false, Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.montar("2026-10-31", "2026-10-01", nsp));
        assertThrows(IllegalArgumentException.class, () -> service.montar("01/10/2026", "2026-10-31", nsp));
    }

    @Test
    void nspECcihAcessamMasEnfermagemComumNao() {
        List<String> p = new RoutePermissionCatalog().requiredPermissionsFor("cdss/indicadores-seguranca").orElseThrow();
        assertTrue(p.contains("pnsp"));
        assertTrue(p.contains("pccih"));
        assertFalse(p.contains("ktcenfermagem"));
    }
}
