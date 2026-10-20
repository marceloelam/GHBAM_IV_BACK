package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.security.RoutePermissionCatalog;

class PlantaoEnfermagemServiceTest {

    @Test
    void horarioDoMesmoDia() {
        LocalDateTime aprazado = LocalDateTime.of(2026, 10, 19, 7, 50);
        assertEquals(LocalDateTime.of(2026, 10, 19, 14, 0), PlantaoEnfermagemService.horarioPrevisto(aprazado, "14:00"));
    }

    @Test
    void horarioDaMadrugadaSeguinte() {
        LocalDateTime aprazado = LocalDateTime.of(2026, 10, 19, 20, 0);
        assertEquals(LocalDateTime.of(2026, 10, 20, 6, 0), PlantaoEnfermagemService.horarioPrevisto(aprazado, "06:00"));
        assertEquals(LocalDateTime.of(2026, 10, 20, 6, 0), PlantaoEnfermagemService.horarioPrevisto(aprazado, "6:00"));
    }

    @Test
    void horarioInvalidoNaoQuebra() {
        assertNull(PlantaoEnfermagemService.horarioPrevisto(LocalDateTime.now(), "--:--"));
        assertNull(PlantaoEnfermagemService.horarioPrevisto(null, "08:00"));
    }

    @Test
    void semUnidadeNaSessaoRecusa() {
        assertThrows(SecurityException.class, () -> new PlantaoEnfermagemService().montar(" "));
    }

    @Test
    void passagemExigeSessaoESituacao() {
        PlantaoEnfermagemService service = new PlantaoEnfermagemService();
        br.com.iv.cdss.dto.PassagemPlantaoRequest r = new br.com.iv.cdss.dto.PassagemPlantaoRequest();
        r.setIdbam(10);
        r.setSituacao("ok");
        assertThrows(SecurityException.class, () -> service.registrarPassagem(r, null));

        br.com.iv.security.AuthenticatedUser enfermeira = new br.com.iv.security.AuthenticatedUser("1", "777", "Enf. Teste",
                null, "HMSFX", null, null, null, null, null, null, null, false, java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.registrarPassagem(r, enfermeira));
        assertThrows(SecurityException.class, () -> service.receberPassagem(1L, null));
        assertThrows(SecurityException.class, () -> service.passagensDoDia("2026-10-19", null));
        assertThrows(IllegalArgumentException.class, () -> service.passagensDoDia("19/10/2026", enfermeira));
    }

    @Test
    void passagemUsaMesmaPermissaoDoPainel() {
        assertTrue(new RoutePermissionCatalog().requiredPermissionsFor("cdss/plantao/passagem/5/receber")
                .orElseThrow().contains("ktcenfermagem"));
    }

    @Test
    void tecnicoDeEnfermagemAcessaPainel() {
        List<String> permissoes = new RoutePermissionCatalog().requiredPermissionsFor("cdss/plantao").orElseThrow();
        assertTrue(permissoes.contains("ktcenfermagem"));
    }
}
