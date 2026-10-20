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

import br.com.iv.cdss.dto.DispositivoInvasivoRequest;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.RoutePermissionCatalog;

class DispositivoInvasivoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 19);

    @Test
    void contaDiasCalendarioIncluindoInsercaoERetirada() {
        // Inserida dia 10, retirada dia 12: 3 dispositivos-dia (10, 11, 12).
        assertEquals(3, DispositivoInvasivoService.diasNoPeriodo(
                LocalDateTime.of(2026, 10, 10, 22, 0), LocalDateTime.of(2026, 10, 12, 8, 0),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), HOJE));
    }

    @Test
    void recortaPeloPeriodoEContaAtivoAteHoje() {
        // Inserida em setembro, ainda ativa: em outubro conta de 1 a 19.
        assertEquals(19, DispositivoInvasivoService.diasNoPeriodo(
                LocalDateTime.of(2026, 9, 25, 10, 0), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), HOJE));
        // Retirada antes do periodo: zero.
        assertEquals(0, DispositivoInvasivoService.diasNoPeriodo(
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 5, 10, 0),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), HOJE));
    }

    @Test
    void dataDeInsercaoValidada() {
        LocalDateTime agora = LocalDateTime.of(2026, 10, 19, 15, 0);
        assertEquals(agora, DispositivoInvasivoService.dataInsercao("", agora));
        assertEquals(LocalDateTime.of(2026, 10, 18, 9, 30), DispositivoInvasivoService.dataInsercao("2026-10-18T09:30", agora));
        assertThrows(IllegalArgumentException.class, () -> DispositivoInvasivoService.dataInsercao("2026-10-20T09:30", agora));
        assertThrows(IllegalArgumentException.class, () -> DispositivoInvasivoService.dataInsercao("2026-05-01T09:30", agora));
        assertThrows(IllegalArgumentException.class, () -> DispositivoInvasivoService.dataInsercao("18/10/2026", agora));
    }

    @Test
    void exigeSessaoETipoValido() {
        DispositivoInvasivoService service = new DispositivoInvasivoService();
        DispositivoInvasivoRequest r = new DispositivoInvasivoRequest();
        r.setIdbam(10);
        r.setTipo("SONDA_QUALQUER");
        assertThrows(SecurityException.class, () -> service.inserir(r, null));
        AuthenticatedUser enfermeira = new AuthenticatedUser("1", "777", "Enf. Teste", null, "HMSFX", null, null,
                null, null, null, null, null, false, Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.inserir(r, enfermeira));
        assertThrows(IllegalArgumentException.class, () -> service.indicadores("2026-10-31", "2026-10-01", enfermeira));
    }

    @Test
    void ccihVeIndicadorMasNaoRegistraDispositivo() {
        RoutePermissionCatalog catalogo = new RoutePermissionCatalog();
        List<String> indicador = catalogo.requiredPermissionsFor("cdss/plantao/dispositivos/indicadores").orElseThrow();
        assertTrue(indicador.contains("pccih"));
        assertFalse(catalogo.requiredPermissionsFor("cdss/plantao/dispositivo").orElseThrow().contains("pccih"));
    }
}
