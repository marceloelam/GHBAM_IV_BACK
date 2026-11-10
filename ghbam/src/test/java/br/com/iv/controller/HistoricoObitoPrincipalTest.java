package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HistoricoObitoPrincipalTest {

    private static Map<String, Object> obito(String titulo, Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("titulo", titulo);
        m.put("data", data);
        return m;
    }

    @Test
    void desfechoSemDataNaoVenceSaidaDoLeito() {
        Map<String, Object> escolhido = HistoricoPacienteController.obitoPrincipal(List.of(
                obito("Obito (desfecho do atendimento)", null),
                obito("Obito (saida do leito/NIR)", "2026-11-02")));
        assertEquals("2026-11-02", escolhido.get("data"));
    }

    @Test
    void sumarioDeObitoTemPrioridade() {
        Map<String, Object> escolhido = HistoricoPacienteController.obitoPrincipal(List.of(
                obito("Obito (resumo de alta)", "2026-11-01"),
                obito("Sumario de obito", "2026-11-02")));
        assertEquals("Sumario de obito", escolhido.get("titulo"));
    }

    @Test
    void semObitoRetornaNulo() {
        assertNull(HistoricoPacienteController.obitoPrincipal(List.of()));
    }
}
