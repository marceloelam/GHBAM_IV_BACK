package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RoutePermissionCatalogRndsLaboratorioTest {

    private final RoutePermissionCatalog catalog = new RoutePermissionCatalog();

    @Test
    void restringeResultadoLaboratorialAosPerfisClinicosAutorizados() {
        List<String> permissoes = catalog.requiredPermissionsFor(
                "interoperabilidade/fhir/resultados-laboratorio/bam/42").orElseThrow();

        assertEquals(List.of("klaboratorio", "kmedico", "kenfermagem", "kadm"), permissoes);
        assertTrue(catalog.requiredPermissionsFor(
                "interoperabilidade/fhir/resultados-laboratorio/bam/42/homologacao").isPresent());
    }
}
