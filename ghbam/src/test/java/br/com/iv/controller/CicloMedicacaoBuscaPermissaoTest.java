package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.iv.security.RoutePermissionCatalog;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

class CicloMedicacaoBuscaPermissaoTest {

    private final RoutePermissionCatalog catalogo = new RoutePermissionCatalog();

    private List<String> permissoes(String rota) {
        return catalogo.requiredPermissionsFor(rota).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pacientes/internadonomenir/MARIA/unidade/TESTE",
            "pacientes/recentes/MARIA/unidade/TESTE",
            "pacientes/por-prontuario/2026-1",
            "bam/idbam/1",
            "bam/idpaciente/1"
    })
    void farmaciaLocalizaPacienteNasRotasDaBusca(String rota) {
        assertTrue(permissoes(rota).contains("kfarmaciacentral"), rota);
        assertTrue(permissoes(rota).contains("pciclomedicacao"), rota);
        assertTrue(permissoes(rota).contains("kenfermagem"), rota);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pacientes/1/prontuario/2026-1",
            "pacientes/MARIA",
            "pacientes/nomenir/MARIA/unidade/TESTE/tipo/x",
            "historicoPaciente/prontuario/2026-1/unidade/TESTE",
            "bam/1"
    })
    void farmaciaNaoGanhaOutrasRotasDePacienteNemHistorico(String rota) {
        assertFalse(permissoes(rota).contains("kfarmaciacentral"), rota);
        assertFalse(permissoes(rota).contains("pciclomedicacao"), rota);
    }

    @Test
    void buscaPorProntuarioExisteSoParaLeitura() throws Exception {
        Method metodo = PacientesController.class.getDeclaredMethod("listarPorProntuario", String.class);
        assertNotNull(metodo.getAnnotation(GET.class));
        assertEquals("/por-prontuario/{prontuario}", metodo.getAnnotation(Path.class).value());
    }
}
