package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RoutePermissionCatalogConferenciaMedicamentoTest {

    private final RoutePermissionCatalog catalog = new RoutePermissionCatalog();

    @Test
    void tecnicoDeEnfermagemConfereEmbalagemMasNaoAcessaRestoDoCdss() {
        List<String> conferencia = catalog.requiredPermissionsFor("cdss/conferencia-medicamento").orElseThrow();
        assertTrue(conferencia.contains("ktcenfermagem"));
        assertTrue(conferencia.contains("kenfermagem"));

        List<String> override = catalog.requiredPermissionsFor("cdss/alertas/override").orElseThrow();
        assertFalse(override.contains("ktcenfermagem"));
    }
}
