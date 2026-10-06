package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HistoricoPacienteParecerTest {
    @Test
    void incluiTodasAsRespostasDoBamComAutoriaUnidadeEConteudo() {
        var controller = new HistoricoPacienteController();
        controller.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(controller.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("bam", 123)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(
                new Object[]{8, "2026-10-05", null, "Especialista A", "UNIDADE A", "Pedido A",
                    "Resposta A", "CRM A", "Solicitante", "CRM S", "CLINICA", "CARDIO", "P1", "2026-10-04"},
                new Object[]{7, null, null, "Especialista B", "UNIDADE B", "Pedido B",
                    "Resposta antiga", "CRM B", "Solicitante", "CRM S", "CLINICA", "NEURO", "P1", "2026-10-03"}));

        var eventos = controller.listarPareceresRespondidos(123);
        assertEquals(2, eventos.size());
        var evento = eventos.get(0);
        assertEquals("PARECER_ESPECIALISTA", evento.get("tipo"));
        assertEquals(123, evento.get("bam"));
        assertEquals("2026-10-05", evento.get("data"));
        assertNull(evento.get("hora"));
        assertEquals("Especialista A", evento.get("profissional"));
        assertEquals("UNIDADE A", evento.get("local"));
        String conteudo = (String) evento.get("conteudo");
        assertTrue(conteudo.contains("Pedido A"));
        assertTrue(conteudo.contains("Resposta do especialista:</strong> Resposta A"));
        assertTrue(conteudo.contains("CRM A"));
        assertTrue(conteudo.contains("CARDIO"));
        assertTrue(((String) eventos.get(1).get("conteudo")).contains("Resposta antiga"));
        assertNull(eventos.get(1).get("data"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(controller.entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("nbam = :bam"));
        assertTrue(sql.getValue().contains("NULLIF(TRIM(desc2), '') IS NOT NULL"));
        assertFalse(sql.getValue().contains("LIMIT 1"));
    }

    @Test
    void bamSemRespostasNaoGeraEvento() {
        var controller = new HistoricoPacienteController();
        controller.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(controller.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("bam", 456)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        assertTrue(controller.listarPareceresRespondidos(456).isEmpty());
    }
}
