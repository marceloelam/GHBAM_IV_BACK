package br.com.iv.producaoassistencial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Linha;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Tipo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProducaoAssistencialRepositoryTest {
    @Test void agregaNoBancoSemExporConteudoClinicoEComParametros() {
        var repo = new ProducaoAssistencialRepository(); repo.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(repo.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{"PARECER_RESPONDIDO", "CARDIO", "Ana", "CRM", Date.valueOf("2026-10-05"), 7L}));
        var result = repo.buscar(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), "U", EnumSet.of(Tipo.PARECER_RESPONDIDO));
        assertEquals(7, result.get(0).total()); assertEquals(LocalDate.of(2026, 10, 5), result.get(0).data());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(repo.entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("SUM(quantidade)"));
        assertTrue(sql.getValue().contains("p.dataresp BETWEEN :inicio AND :fim"));
        assertTrue(sql.getValue().contains("NULLIF(TRIM(p.desc2), '') IS NOT NULL"));
        assertTrue(sql.getValue().contains(":unidade"));
        assertFalse(sql.getValue().contains("FROM anamneseinternacao"));
        verify(query).setParameter("unidade", "U");
    }
    @Test void fontesSelecionadasTemCriteriosDeConclusaoENaoMisturamVisitaComAtendimento() {
        assertTrue(ProducaoAssistencialRepository.consulta(Tipo.MULTIDISCIPLINAR).contains("a.status = 'FINALIZADO'"));
        assertTrue(ProducaoAssistencialRepository.consulta(Tipo.ODONTOLOGICO).contains("o.status = 'ASSINADO'"));
        assertTrue(ProducaoAssistencialRepository.consulta(Tipo.EVOLUCAO_MEDICA).contains("COALESCE(e.inativo, FALSE) = FALSE"));
        assertTrue(ProducaoAssistencialRepository.consulta(Tipo.APS_VISITA).contains("visitasDomiciliares"));
        assertTrue(ProducaoAssistencialRepository.consulta(Tipo.APS_DOMICILIAR).contains("atendimentosDomiciliares"));
        String procedimentos = ProducaoAssistencialRepository.consulta(Tipo.APS_PROCEDIMENTO);
        assertTrue(procedimentos.contains("procedimentosRealizados"));
        assertTrue(procedimentos.contains("quantidade"));
        assertTrue(procedimentos.contains("jsonb_typeof"));
        assertTrue(procedimentos.contains("'PRONTA', 'ENVIADA', 'REJEITADA'"));
        assertFalse(procedimentos.contains("'RASCUNHO'"));
    }
    @Test void odontologiaTipaDatasEIncluiTodoOUltimoDiaNosDetalhesENoResumo() {
        for (String sql : List.of(ProducaoAssistencialRepository.consulta(Tipo.ODONTOLOGICO), ProducaoAssistencialRepository.consultaDetalhes(Tipo.ODONTOLOGICO))) {
            assertTrue(sql.contains("o.criado_em >= CAST(:inicio AS date)"));
            assertTrue(sql.contains("o.criado_em < CAST(:fim AS date) + INTERVAL '1 day'"));
            assertTrue(sql.contains("o.status = 'ASSINADO'"));
            assertTrue(sql.contains(":unidade"));
            assertFalse(sql.contains("o.criado_em < :fim +"));
        }
    }
    @Test void resolveCrmEMatriculaSemMisturarHomonimos() {
        var repo = new ProducaoAssistencialRepository(); repo.entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(repo.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Object[]{"M1", "Ana", "CRM1", ""}, new Object[]{"M2", "Ana", "CRM2", ""}));
        var date = LocalDate.of(2026, 10, 5);
        var result = repo.normalizarAutores(List.of(
                new Linha("MEDICO", "Medicina", "Ana", "M1", "M1", date, 2),
                new Linha("PARECER_RESPONDIDO", "CARDIO", "Ana", "CRM1", "", date, 1),
                new Linha("ANAMNESE", "Medicina", "Ana", "CRM2", "", date, 3),
                new Linha("ANAMNESE", "Medicina", "Ana", "Nao informado", "", date, 1)), "U");
        assertEquals("M1", result.get(0).matricula());
        assertEquals("M1", result.get(1).matricula());
        assertEquals("M2", result.get(2).matricula());
        assertEquals("", result.get(3).matricula());
        assertEquals(7, result.stream().mapToLong(Linha::total).sum());
    }
}
