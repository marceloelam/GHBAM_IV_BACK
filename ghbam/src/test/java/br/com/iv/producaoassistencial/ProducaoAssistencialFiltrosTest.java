package br.com.iv.producaoassistencial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import br.com.iv.producaomedica.ProducaoMedicaRepository;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Tipo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProducaoAssistencialFiltrosTest {
    @Test void cadastraFiltrosDaUnidadeSemExigirProducaoFinalizadaNoPeriodo() {
        var repo = new ProducaoAssistencialRepository();
        repo.entityManager = mock(EntityManager.class);
        var query = mock(Query.class);
        when(repo.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("unidade", "U")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{"Bia", "F1", "CREFITO1", "FISIOTERAPEUTA", "", "223605", false, false, false, false, true, false, false, false, false}));
        var filtros = repo.opcoesFiltros("U", EnumSet.of(Tipo.MULTIDISCIPLINAR));
        assertEquals(1, filtros.profissionais().size());
        assertEquals("F1", filtros.profissionais().get(0).matricula());
        assertTrue(filtros.profissionais().get(0).categorias().contains("Fisioterapia"));
        assertTrue(filtros.profissionais().get(0).categorias().contains("223605"));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(repo.entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("FROM servidor s"));
        assertTrue(sql.getValue().contains(":unidade"));
        assertTrue(sql.getValue().contains("COALESCE(s.fisioterapia, FALSE)"));
        assertFalse(sql.getValue().contains("BETWEEN"));
        assertFalse(sql.getValue().contains("FINALIZADO"));
    }
    @Test void retornarCadastroNaoAumentaOsTotaisDoRelatorioVazio() {
        var repo = mock(ProducaoAssistencialRepository.class);
        var medica = mock(ProducaoMedicaRepository.class);
        var date = LocalDate.of(2026, 10, 6);
        when(repo.buscar(date, date, "U", EnumSet.of(Tipo.MULTIDISCIPLINAR))).thenReturn(List.of());
        when(repo.normalizarAutores(anyList(), eq("U"))).thenReturn(List.of());
        when(repo.opcoesFiltros("U", EnumSet.of(Tipo.MULTIDISCIPLINAR))).thenReturn(new ProducaoAssistencialService.OpcoesFiltros(List.of(new ProducaoAssistencialService.FiltroProfissional("Bia", "1", "F1", List.of("Fisioterapia")))));
        var relatorio = new ProducaoAssistencialService(repo, medica).gerar(date, date, "U", "MULTIDISCIPLINAR");
        assertEquals(0, relatorio.totalGeral());
        assertTrue(relatorio.linhas().isEmpty());
        assertEquals(1, relatorio.opcoesFiltros().profissionais().size());
        verifyNoInteractions(medica);
    }
}
