package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RoutePermissionCatalogFaturamentoSusTest {

    private final RoutePermissionCatalog catalog = new RoutePermissionCatalog();

    @Test
    void reabrirCompetenciaExigePermissaoPropriaSemAceitarKfaturamentoGenerico() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("faturamento/competencias/reabrir/5");

        assertTrue(permissoes.isPresent());
        assertTrue(permissoes.get().contains("pfaturamentosusreabrir"));
        assertTrue(permissoes.get().contains("kadm"));
        assertFalse(permissoes.get().contains("kfaturamento"));
    }

    @Test
    void fecharCompetenciaExigeKfaturamentoEPermissaoDeFechar() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("faturamento/competencias/fechar/5");

        assertTrue(permissoes.isPresent());
        assertTrue(permissoes.get().containsAll(List.of("kfaturamento", "pfaturamentosusfechar", "kadm")));
    }

    @Test
    void reprocessarProducaoExigePermissaoDeCorrigirEVenceOPrefixoGenerico() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("faturamento/producao/reprocessar/12");

        assertTrue(permissoes.isPresent());
        assertTrue(permissoes.get().contains("pfaturamentosuscorrigir"));
    }

    @Test
    void listagemDeProducaoUsaSomentePermissaoGenericaDeFaturamento() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("faturamento/producao");

        assertTrue(permissoes.isPresent());
        assertEquals(List.of("kfaturamento", "kadm"), permissoes.get());
    }

    @Test
    void importarSigtapExigePermissaoPropria() {
        Optional<List<String>> permissoes = catalog.requiredPermissionsFor("sigtap/importar");

        assertTrue(permissoes.isPresent());
        assertTrue(permissoes.get().contains("sigtapimportar"));
        assertFalse(permissoes.get().contains("kfaturamento"));
    }
}
