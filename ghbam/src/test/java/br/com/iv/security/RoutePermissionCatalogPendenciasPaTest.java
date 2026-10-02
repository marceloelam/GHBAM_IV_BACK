package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RoutePermissionCatalogPendenciasPaTest {

    private final RoutePermissionCatalog catalog = new RoutePermissionCatalog();

    @Test
    void filaGerencialDePendenciasSoParaFaturamentoEAdm() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("jornada-pa/pendencias");

        assertTrue(permissoes.isPresent());
        assertEquals(List.of("kfaturamento", "kadm"), permissoes.get());
    }

    @Test
    void baixaDePendenciaNaJornadaSegueRegraGeralDaJornada() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("jornada-pa/pendencias-baixas/10");

        assertTrue(permissoes.isPresent());
        assertTrue(permissoes.get().containsAll(List.of("kmedico", "kenfermagem")));
    }

    @Test
    void jornadaPorBamContinuaAbertaParaAssistencia() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("jornada-pa/bam/10");

        assertTrue(permissoes.isPresent());
        assertTrue(permissoes.get().containsAll(List.of("kmedico", "kenfermagem")));
    }
}
