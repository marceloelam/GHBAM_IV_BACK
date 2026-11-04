package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.iv.controller.ProntuarioClinicoItemController;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import br.com.iv.security.RoutePermissionCatalog;
import jakarta.ws.rs.GET;

class ProntuarioClinicoAlergiasAtivasTest {
    private final RoutePermissionCatalog catalogo = new RoutePermissionCatalog();

    private ProntuarioClinicoItem alergia(String status, String titulo, String descricao) {
        ProntuarioClinicoItem item = new ProntuarioClinicoItem();
        item.setStatus(status);
        item.setTitulo(titulo);
        item.setDescricao(descricao);
        return item;
    }

    private ProntuarioClinicoItemService servico() {
        ProntuarioClinicoItemService service = new ProntuarioClinicoItemService();
        service.repository = mock(ProntuarioClinicoItemRepository.class);
        return service;
    }

    @Test
    void ignoraInativaUsaDescricaoNaFaltaDeTituloEDescartaVazia() {
        ProntuarioClinicoItemService service = servico();
        when(service.repository.listarPorPacienteTipo(eq(7), any(), eq("TODAS"))).thenReturn(List.of(
                alergia("ATIVO", " DIPIRONA ", null),
                alergia(null, "", "PENICILINA"),
                alergia("inativo", "LATEX", null),
                alergia("ATIVO", " ", " ")));

        assertEquals(List.of("DIPIRONA", "PENICILINA"), service.listarAlergiasAtivas(7));
    }

    @Test
    void falhaDaConsultaNaoViraListaVazia() {
        ProntuarioClinicoItemService service = servico();
        when(service.repository.listarPorPacienteTipo(eq(7), any(), eq("TODAS"))).thenThrow(new IllegalStateException("banco"));

        assertThrows(IllegalStateException.class, () -> service.listarAlergiasAtivas(7));
    }

    @Test
    void rotaLiberaMedicacaoESeguraQuemJaLia() {
        List<String> permissoes = catalogo.requiredPermissionsFor("prontuario-clinico/alergias-ativas/7").orElseThrow();
        assertTrue(permissoes.containsAll(List.of("kmedicacao", "ktcenfermagem", "kconsultorio_medico", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm")));
        assertFalse(permissoes.contains("kconsultorio_admin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "prontuario-clinico",
            "prontuario-clinico/7",
            "prontuario-clinico/7/inativar",
            "prontuario-clinico/paciente/7/unidade/TESTE",
            "prontuario-clinico/paciente/7/tipo/ALERGIA/unidade/TODAS",
            "prontuario-clinico/alergia-triagem"
    })
    void perfisDaLeituraNaoGanhamOsDemaisItensNemGravacao(String rota) {
        List<String> permissoes = catalogo.requiredPermissionsFor(rota).orElseThrow();
        assertFalse(permissoes.contains("kmedicacao"), rota);
        assertFalse(permissoes.contains("ktcenfermagem"), rota);
        assertFalse(permissoes.contains("kconsultorio_medico"), rota);
    }

    @Test
    void leituraNaoGrava() throws Exception {
        Method metodo = ProntuarioClinicoItemController.class.getDeclaredMethod("listarAlergiasAtivas", int.class);
        assertNotNull(metodo.getAnnotation(GET.class));
    }
}
